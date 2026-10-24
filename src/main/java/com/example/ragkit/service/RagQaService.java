package com.example.ragkit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索增强问答的编排层：检索 → 阈值过滤 → 选提示词 → 调模型 → 降级。
 *
 * <p><b>为什么命中和不命中都要调模型（本项目踩过的最大的坑）</b>：
 * 初版设计成"检索没命中就返回一句固定话术、不调模型"，理由是省一次远程调用。
 * 实际用下来翻车了 —— 用户说"你好"也走这条路，被回一句"官方资料中暂时无法找到答案"。
 * <b>根因是把"库里没检索到"和"这个问题不该回答"划了等号。</b>
 * 检索为空只说明知识库里没有，不代表模型不能正常陪用户聊。
 *
 * <p>所以现在：命中与未命中<b>都调模型</b>，区别只在于用哪套 system prompt ——
 * 命中时严格照资料作答，未命中时保留人设正常对话、遇到具体政策承认没查到。
 * 代价是每次都多几秒延迟，这个代价是值得的。
 *
 * <p>阈值判断放在这里而不是 {@link KnowledgeRetrieveService} 里：
 * 检索服务只回答"哪些最像"，不回答"够不够格"，后者是业务判断。
 * 这样拆完，检索服务能被推荐、去重、聚类等其他场景直接复用。
 */
@Service
public class RagQaService {

    /**
     * 命中时使用的提示词：严格依据资料，另起一段给建议。
     * 包级可见是为了让测试能直接引用，避免同一份文案在测试里抄第二遍。
     */
    static final String ANSWER_SYSTEM_PROMPT = """
            你是"小政"，在校大学生身边的 AI 辅导员助手，语气亲切自然，像一位熟悉的学长学姐。

            回答分两部分：

            一、直接回答问题。资料里有什么就说什么，数字、条件、时间必须和资料完全一致，一个字不许编。
            不要出现"根据提供的文件""资料片段"这类说法，就像你本来就知道这些信息一样自然地说出来，
            也不要引用任何片段编号。

            二、回答完另起一段，以"我的建议："开头，给出 2-3 条具体可执行的建议
            （比如可以参加哪些比赛、怎么准备、该找谁咨询），不要说"努力学习"这种空话。
            这一段开头必须注明：以下是我的建议，不是官方规定，具体以学校文件为准。

            如果资料不足以回答问题的某一部分，只说这一部分无法确认，不要编。
            """;

    /** 未命中时使用的提示词：正常聊天，但涉及具体政策必须承认没查到。 */
    static final String CHAT_SYSTEM_PROMPT = """
            你是"小政"，在校大学生身边的 AI 辅导员助手，语气亲切自然，像一位熟悉的学长学姐。

            你现在手上没有任何学校官方资料。像朋友一样正常聊天：问候、寒暄、聊学习生活、
            解释通用概念都可以。聊天时不要提到"文件""资料"这类词。

            但如果用户问的是学校或国家的具体政策（金额、条件、时间、流程），
            要坦诚说明你现在没有查到官方文件，给不了准确数字，
            并建议他去问辅导员、学工处或看学校官网。不许凭印象编造任何具体数字或规定。
            """;

    /** 降级话术，包级可见便于测试断言 */
    static final String FALLBACK_MESSAGE = "AI 服务暂时不可用，请稍后重试";

    /**
     * 相似度阈值。实测依据：相关问题 0.63~0.75，不相关问题 0.40~0.45，取中间偏保守。
     * 已知不足：样本量小，且硬编码在此处，下一步应外移到配置文件并用小评测集反推。
     */
    private static final double SCORE_THRESHOLD = 0.55;

    /** 喂给模型的资料片段上限 */
    private static final int TOP_K = 3;

    private static final Logger log = LoggerFactory.getLogger(RagQaService.class);

    private final KnowledgeRetrieveService retrieveService;
    private final OllamaChatClient chatClient;

    public RagQaService(KnowledgeRetrieveService retrieveService, OllamaChatClient chatClient) {
        this.retrieveService = retrieveService;
        this.chatClient = chatClient;
    }

    /**
     * 问答结果。
     *
     * @param answer   模型回复正文；降级时为 {@link #FALLBACK_MESSAGE}
     * @param hit      是否命中了知识库（决定用的是哪套提示词）
     * @param degraded 是否走了降级
     */
    public record AskResult(String answer, boolean hit, boolean degraded) {}

    /**
     * 提问并获取回答。
     *
     * <p>刻意<b>没有</b>加 {@code @Transactional}：方法内含一次最长 60 秒的远程调用，
     * 包在事务里会让数据库连接在整个远程调用期间被占住，并发一上来连接池就被拖垮。
     *
     * @param question 问题原文，不能为 null 或空白
     * @throws IllegalArgumentException question 为 null 或空白时抛出
     */
    public AskResult ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String trimmed = question.trim();

        try {
            List<ScoredChunk> hits = pickRelevant(retrieveService.retrieve(trimmed, TOP_K));
            String systemPrompt = hits.isEmpty() ? CHAT_SYSTEM_PROMPT : ANSWER_SYSTEM_PROMPT;
            String answer = chatClient.chat(systemPrompt, buildUserPrompt(trimmed, hits));
            return new AskResult(answer, !hits.isEmpty(), false);
        } catch (RestClientException e) {
            // 模型服务不可用
            log.error("模型调用失败, question={}", trimmed, e);
            return new AskResult(FALLBACK_MESSAGE, false, true);
        } catch (Exception e) {
            // 检索本身也可能抛异常（脏向量导致余弦计算抛出 IllegalArgumentException，
            // 检索层按设计不 catch）。对用户来说「服务出问题了」就够了，
            // 具体是模型挂了还是数据脏了，靠上面的 error 日志区分。
            log.error("问答处理失败（模型调用或资料检索异常）, question={}", trimmed, e);
            return new AskResult(FALLBACK_MESSAGE, false, true);
        }
    }

    /** 按阈值筛掉低分项。检索层不做过滤，这一步是业务判断。 */
    private List<ScoredChunk> pickRelevant(List<ScoredChunk> results) {
        List<ScoredChunk> hits = new ArrayList<>();
        for (ScoredChunk chunk : results) {
            if (chunk.score() >= SCORE_THRESHOLD) {
                hits.add(chunk);
            }
        }
        return hits;
    }

    /** 用结构化分隔告诉模型哪部分是依据、哪部分是要回答的东西。 */
    private String buildUserPrompt(String question, List<ScoredChunk> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【资料】\n");
        for (int i = 0; i < hits.size(); i++) {
            prompt.append("【片段")
                    .append(i + 1)
                    .append("】\n")
                    .append(hits.get(i).chunk().getContent())
                    .append("\n");
        }
        prompt.append("【问题】\n")
                .append(question);
        return prompt.toString();
    }
}

package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.repository.KnowledgeChunkRepository;
import com.example.ragkit.utils.CosineSimilarity;
import com.example.ragkit.utils.VectorJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识检索服务。
 *
 * <p>处理流程：查询向量化 → 取出全部文档块 → 逐条计算余弦相似度
 * → 按分数降序 → 截取 Top-K。
 *
 * <p>注意：全量遍历仅适用于文档块数量较小的场景。
 * 数据量增长后需引入向量索引替代线性扫描。
 */
@Service
public class KnowledgeRetrieveService {

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public KnowledgeRetrieveService(KnowledgeChunkRepository knowledgeChunkRepository, EmbeddingClient embeddingClient, ObjectMapper objectMapper) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }


    /**
     * @param question 用户的问题原文，不能为 null 或空白
     * @param topK 返回的条数，必须为正数
     * @return 按相似度降序的结果，最多 topK 条；可能包含低分项，本方法不做阈值过滤
     * @throws IllegalArgumentException question 为 null/空白、或 topK ≤ 0 时抛出
     */
    public List<ScoredChunk> retrieve(String question, int topK) {
        //判断参数
        if (question == null||question.isBlank()) {
            throw new IllegalArgumentException("问题不能为null或空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK应为正数");
        }

        List<List<Double>> queryVector = embeddingClient.embed(List.of(question));
        // embed 对空输入会短路返回空 List；这里防的是上游行为变化，不是本方法的输入问题
        if (queryVector == null || queryVector.isEmpty()) {
            throw new IllegalArgumentException("问题向量列表为空");
        }
        List<Double> qVector = queryVector.get(0);

        List<KnowledgeChunk> chunks = knowledgeChunkRepository.findAll();

        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            List<Double> chunkVector = VectorJson.fromJson(objectMapper,chunk.getEmbedding());
            double score = CosineSimilarity.cosine(qVector,chunkVector);
            scoredChunks.add(new ScoredChunk(chunk, score));
        }

        scoredChunks.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        return scoredChunks.stream()
                .limit(topK)
                .collect(Collectors.toList());

    }
}

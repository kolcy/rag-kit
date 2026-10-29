package com.example.ragkit.eval;

/**
 * 一条评测用例。
 *
 * @param id            编号，例如 A01 / C03 / E02
 * @param category      类型：直接问 / 口语化 / 精确词 / 跨文档 / 负样本
 * @param question      要问的问题
 * @param expectDoc     期望命中的文档（docKey 的一部分即可，留空表示不限文档）
 * @param expectKeyword 期望命中块里必须出现的文字（从原文里摘，别凭印象写）；
 *                      留空表示这是一条负样本——库里不该有答案
 */
public record EvalCase(
        String id,
        String category,
        String question,
        String expectDoc,
        String expectKeyword
) {
    /** 负样本：expectKeyword 为空 */
    public boolean isNegative() {
        return expectKeyword == null || expectKeyword.isBlank();
    }

    /** 正样本但有缺失字段的，跑起来没意义 */
    public boolean isUsable() {
        return question != null && !question.isBlank();
    }
}

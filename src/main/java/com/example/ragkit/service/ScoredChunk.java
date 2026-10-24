package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;

/**
 * 如果只留文本，调用方拿到手就无法判断命中与否，只能盲目调模型产生幻觉
 *
 */
public record ScoredChunk(KnowledgeChunk chunk, double score) {

}
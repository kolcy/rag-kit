package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.repository.KnowledgeChunkRepository;
import com.example.ragkit.utils.HashUtil;
import com.example.ragkit.utils.VectorJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrieveServiceTest {

    private static final double DELTA = 1e-3;
    @Mock
    private KnowledgeChunkRepository repo;
    @Mock
    private EmbeddingClient embeddingClient;

    private final ObjectMapper om = new ObjectMapper();   // 真的，不 mock
    private KnowledgeRetrieveService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRetrieveService(repo, embeddingClient, om);
    }

    /**
     * 造一条片段：向量用 2 维，分数能手算
     */
    private KnowledgeChunk chunk(String content, List<Double> vector) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setDocKey("doc-test");
        c.setDocHash(HashUtil.sha256("测试文档"));
        c.setChunkIndex(0);
        c.setContent(content);
        c.setEmbedding(VectorJson.toJson(om, vector));   // 走真实序列化
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    @Test
    @DisplayName("正常检索：按分数降序返回 top-K")
    void retrieve_returnsTopKSortedByScoreDesc() {
        KnowledgeChunk a = chunk("A", List.of(1.0, 0.0));
        KnowledgeChunk b = chunk("B", List.of(1.0, 1.0));
        KnowledgeChunk c = chunk("C", List.of(0.0, 1.0));

        when(repo.findAll()).thenReturn(List.of(a, b, c));
        when(embeddingClient.embed(anyList())).thenReturn(List.of(List.of(1.0, 0.0)));

        List<ScoredChunk> result = service.retrieve("奖学金多少钱", 2);

        // 第一段：规模 —— 返回几条？
        assertEquals(2, result.size(), "应返回 top-2");

        // 第二段：顺序 —— 排得对不对？
        assertSame(a, result.get(0).chunk(), "第 1 名应为 A");
        assertSame(b, result.get(1).chunk(), "第 2 名应为 B");

        // 第三段：内容 —— 分数算得对不对？
        assertEquals(1.0, result.get(0).score(), DELTA, "A 与查询完全同向，余弦应为 1");
        assertEquals(1 / Math.sqrt(2), result.get(1).score(), DELTA, "B 的余弦应为 1/√2");
    }


    @Test
    @DisplayName("topK 大于总数：返回全部")
    void retrieve_topKExceedsTotal_returnsAll() {
        KnowledgeChunk a = chunk("A", List.of(1.0, 0.0));
        KnowledgeChunk b = chunk("B", List.of(1.0, 1.0));
        KnowledgeChunk c = chunk("C", List.of(0.0, 1.0));

        when(repo.findAll()).thenReturn(List.of(a, b, c));
        when(embeddingClient.embed(anyList())).thenReturn(List.of(List.of(1.0, 0.0)));

        List<ScoredChunk> result = service.retrieve("奖学金", 10);

        // 规模：只有 3 条，不会多也不会少
        assertEquals(3, result.size(), "topK=10 但库里只有 3 条，应全部返回");

        // 顺序：降序，仍是 a、b、c
        assertSame(a, result.get(0).chunk(), "第 1 名应为 A");
        assertSame(b, result.get(1).chunk(), "第 2 名应为 B");
        assertSame(c, result.get(2).chunk(), "第 3 名应为 C");

        // 内容：分数算对
        assertEquals(1.0, result.get(0).score(), DELTA);
        assertEquals(0.7071, result.get(1).score(), DELTA);
        assertEquals(0.0, result.get(2).score(), DELTA, "C 与查询正交，余弦为 0");
    }


    @Test
    @DisplayName("知识库为空：返回空列表（不是 null）")
    void retrieve_emptyKnowledgeBase_returnsEmptyList() {
        when(repo.findAll()).thenReturn(List.of());
        // 这条必须 stub embed：不 stub 的话 Mockito 返回 null，会撞上你实现里那个判空
        when(embeddingClient.embed(anyList())).thenReturn(List.of(List.of(1.0, 0.0)));

        List<ScoredChunk> result = service.retrieve("奖学金", 3);

        // 两个都要：非 null + 为空
        assertNotNull(result, "空库应返回空列表，不能返回 null");
        assertTrue(result.isEmpty(), "结果应为空列表");
    }


    @Test
    @DisplayName("topK 非正数：抛 IllegalArgumentException")
    void retrieve_invalidTopK_throws() {
        // 一行 stub 都别写：参数校验在最前面，根本走不到 embed
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("奖学金", 0));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("奖学金", -1));
    }


    @Test
    @DisplayName("问题为空：抛 IllegalArgumentException")
    void retrieve_blankQuestion_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("", 3));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("   ", 3));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve(null, 3));
    }
}


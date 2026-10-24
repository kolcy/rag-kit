package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.utils.TextNormalizer;
import com.example.ragkit.utils.TextSplitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private KnowledgeChunkStore store;

    @Captor
    private ArgumentCaptor<List<KnowledgeChunk>> entitiesCaptor;

    private KnowledgeIngestService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIngestService(embeddingClient, store, new ObjectMapper());
    }

    private static List<Double> fakeVector() {
        return Collections.nCopies(1024, 0.1);
    }

    @Test
    @DisplayName("正常入库：切分→向量化→交给 store，chunkIndex 从 0 连续")
    void ingest_normal() {
        String unit = "第一章 学生应当遵守学校规章制度并按时参加教育教学活动。";
        String text = unit.repeat(80);

        // 假 embedding：入参几段就返回几条向量
        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> fakeVector()).toList();
        });

        // when
        int n = service.ingest("xshsc-001", "学生手册", text);

        // then
        verify(store).replaceChunks(eq("xshsc-001"), entitiesCaptor.capture());
        List<KnowledgeChunk> saved = entitiesCaptor.getValue();

        assertEquals(7, saved.size(), "2240 字 / 步长 350 应该是 7 段");
        assertEquals(7, n);
        for (int i = 0; i < saved.size(); i++) {
            assertEquals(i, saved.get(i).getChunkIndex());
            assertEquals("xshsc-001", saved.get(i).getDocumentId());
            assertEquals("学生手册", saved.get(i).getDocumentTitle());
            assertNull(saved.get(i).getId(), "入库前绝不能有主键值");
        }
        assertTrue(text.startsWith(saved.get(0).getContent()), "第 0 段必须是原文开头");
    }

    @Test
    @DisplayName("空文本：返回 0，且不调 embedding、不碰数据库")
    void ingest_emptyText() {
        int n = service.ingest("xshsc-001", "学生手册", "");

        assertEquals(0, n);
        verifyNoInteractions(embeddingClient);
        verifyNoInteractions(store);
    }
    @Test
    @DisplayName("向量数与片段数不一致：立刻抛异常，绝不能碰数据库")
    void ingest_vectorCountMismatch() {
        String text = "第一章 学生应当遵守学校规章制度并按时参加教育教学活动。".repeat(80);

        // 只返回 1 条向量，但切出来有 7 段
        when(embeddingClient.embed(anyList())).thenReturn(List.of(fakeVector()));

        assertThrows(IllegalStateException.class,
                () -> service.ingest("xshsc-001", "学生手册", text));

        verify(store, never()).replaceChunks(anyString(), anyList());
    }

    @Test
    @DisplayName("content 与切分结果一一对应，不能张冠李戴")
    void ingest_contentMatchesChunks() {
        String text = "第一章 学生应当遵守学校规章制度并按时参加教育教学活动。".repeat(80);

        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> fakeVector()).toList();
        });

        service.ingest("xshsc-001", "学生手册", text);

        verify(store).replaceChunks(eq("xshsc-001"), entitiesCaptor.capture());
        List<KnowledgeChunk> saved = entitiesCaptor.getValue();

        List<String> expected = new TextSplitter().split(TextNormalizer.normalize(text));
        assertEquals(expected.size(), saved.size(), "入库条数应等于切分段数");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), saved.get(i).getContent(),
                    "第 " + i + " 段内容必须等于切分结果的第 " + i + " 段");
        }
        assertNotEquals(text, saved.get(0).getContent(), "存的必须是片段，不是整篇原文");
    }

    @Test
    @DisplayName("embedding 已序列化成 JSON 数组字符串")
    void ingest_embeddingIsJsonString() {
        String text = "第一章 学生应当遵守学校规章制度并按时参加教育教学活动。".repeat(80);

        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> fakeVector()).toList();
        });

        service.ingest("xshsc-001", "学生手册", text);

        verify(store).replaceChunks(eq("xshsc-001"), entitiesCaptor.capture());
        List<KnowledgeChunk> saved = entitiesCaptor.getValue();

        String embedding = saved.get(0).getEmbedding();
        assertNotNull(embedding, "embedding 不能为 null");
        assertTrue(embedding.startsWith("["), "应该是 JSON 数组，以 [ 开头");
        assertTrue(embedding.endsWith("]"), "应该是 JSON 数组，以 ] 结尾");
        assertTrue(embedding.contains(","), "1024 个数字之间应该有逗号");
        assertDoesNotThrow(() -> new ObjectMapper().readValue(embedding, List.class),
                "必须能被 Jackson 正常反序列化回来");
    }

}

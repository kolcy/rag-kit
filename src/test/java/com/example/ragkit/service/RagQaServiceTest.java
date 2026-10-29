package com.example.ragkit.service;

import com.example.ragkit.entity.KnowledgeChunk;
import com.example.ragkit.utils.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 编排层的单元测试。
 *
 * <p><b>断言目标为什么不是「有没有调模型」</b>：命中与未命中现在都调模型，
 * 所以 {@code verify(chatClient, never()).chat(...)} 已经彻底失效 —— 它对两条分支给出同样的结果。
 * 真正的区分点是「传进去的是哪套 prompt」。
 *
 * <p>通用规则：<b>两条分支的行为靠参数区分时，断言目标要从「是否调用」转移到「传了什么」。</b>
 */
@ExtendWith(MockitoExtension.class)
class RagQaServiceTest {

    @Mock
    private KnowledgeRetrieveService retrieveService;

    @Mock
    private OllamaChatClient chatClient;

    @Captor
    private ArgumentCaptor<String> systemPromptCaptor;

    @Captor
    private ArgumentCaptor<String> userPromptCaptor;

    private RagQaService service;

    @BeforeEach
    void setUp() {
        service = new RagQaService(retrieveService, chatClient);
    }

    private KnowledgeChunk chunk(String content) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setId(1L);
        c.setDocKey("doc-001");
        c.setDocHash(HashUtil.sha256("国家奖学金评审办法"));
        c.setChunkIndex(0);
        c.setContent(content);
        c.setEmbedding("[0.1,0.2,0.3]");
        return c;
    }

    @Test
    @DisplayName("命中：使用 ANSWER 提示词，且 user prompt 里带上资料与问题")
    void ask_hit_usesAnswerPrompt() {
        when(retrieveService.retrieve(anyString(), anyInt()))
                .thenReturn(List.of(new ScoredChunk(chunk("每生每年 8000 元"), 0.72)));
        when(chatClient.chat(anyString(), anyString())).thenReturn("每生每年 8000 元。");

        RagQaService.AskResult result = service.ask("国家奖学金多少钱？");

        assertTrue(result.hit(), "相似度 0.72 应判定为命中");
        assertFalse(result.degraded());
        assertEquals("每生每年 8000 元。", result.answer());

        verify(chatClient).chat(systemPromptCaptor.capture(), userPromptCaptor.capture());
        assertEquals(RagQaService.ANSWER_SYSTEM_PROMPT, systemPromptCaptor.getValue());

        String userPrompt = userPromptCaptor.getValue();
        assertTrue(userPrompt.contains("每生每年 8000 元"), "资料内容应拼进 user prompt");
        assertTrue(userPrompt.contains("【问题】"), "user prompt 应有结构化分隔");
        assertTrue(userPrompt.contains("国家奖学金多少钱？"));
    }

    @Test
    @DisplayName("未命中：仍然调用模型，只是换成 CHAT 提示词")
    void ask_miss_stillCallsModelWithChatPrompt() {
        when(retrieveService.retrieve(anyString(), anyInt()))
                .thenReturn(List.of(new ScoredChunk(chunk("无关内容"), 0.42)));
        when(chatClient.chat(anyString(), anyString())).thenReturn("你好呀，有什么可以帮你的？");

        RagQaService.AskResult result = service.ask("你好");

        assertFalse(result.hit(), "相似度 0.42 应判定为未命中");
        assertFalse(result.degraded());
        // 关键断言：未命中也要有答案，而不是一句"查不到"
        assertEquals("你好呀，有什么可以帮你的？", result.answer());

        verify(chatClient).chat(systemPromptCaptor.capture(), userPromptCaptor.capture());
        assertEquals(RagQaService.CHAT_SYSTEM_PROMPT, systemPromptCaptor.getValue());
    }

    @Test
    @DisplayName("阈值边界：分数恰好等于 0.55 算命中（>= 不是 >）")
    void ask_scoreExactlyAtThreshold_isHit() {
        when(retrieveService.retrieve(anyString(), anyInt()))
                .thenReturn(List.of(new ScoredChunk(chunk("边界内容"), 0.55)));
        when(chatClient.chat(anyString(), anyString())).thenReturn("ok");

        assertTrue(service.ask("边界问题").hit(), "阈值判断是 >=，0.55 应命中");
    }

    @Test
    @DisplayName("模型异常：降级返回可读提示，不把异常抛给调用方")
    void ask_modelThrows_degrades() {
        when(retrieveService.retrieve(anyString(), anyInt()))
                .thenReturn(List.of(new ScoredChunk(chunk("内容"), 0.70)));
        when(chatClient.chat(anyString(), anyString()))
                .thenThrow(new RestClientException("ollama 挂了"));

        RagQaService.AskResult result = service.ask("国家奖学金多少钱？");

        assertTrue(result.degraded());
        assertEquals(RagQaService.FALLBACK_MESSAGE, result.answer());
        assertFalse(result.hit());
    }

    @Test
    @DisplayName("空白问题直接抛参数异常，不浪费一次远程调用")
    void ask_blankQuestion_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.ask("   "));
        assertThrows(IllegalArgumentException.class, () -> service.ask(null));
        verifyNoInteractions(retrieveService);
        verifyNoInteractions(chatClient);
    }
}

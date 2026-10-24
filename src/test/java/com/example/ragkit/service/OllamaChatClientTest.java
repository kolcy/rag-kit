package com.example.ragkit.service;

import com.example.ragkit.config.OllamaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OllamaChatClient 测试：用 MockRestServiceServer 假扮 Ollama，不发真实 HTTP 请求。
 *
 * <p>守的是两件容易被悄悄改坏的事：
 * 一是响应里答案在 {@code message.content}（写成别的字段名会静默返回 null，不报错）；
 * 二是请求体里必须显式带 {@code stream:false}（该参数默认是 true，
 * 漏掉后 Ollama 返回的是流式 NDJSON，Jackson 只能填出一个空壳对象）。
 */
class OllamaChatClientTest {

    private static final String SYSTEM = "你是辅导员助理";
    private static final String USER = "国家奖学金多少钱";

    /**
     * 只放 message 一个字段：RestClient.builder() 用的是默认 ObjectMapper，
     * 未知字段会触发 FAIL_ON_UNKNOWN_PROPERTIES，多余字段反而让测试失败。
     */
    private static final String RESPONSE_JSON = """
            {"message":{"role":"assistant","content":"每生每年8000元"}}
            """;

    private MockRestServiceServer server;
    private OllamaChatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        OllamaProperties props = new OllamaProperties();
        props.setChatModel("qwen2.5:7b");

        client = new OllamaChatClient(builder.build(), props);
    }

    @Test
    @DisplayName("正常返回：取到的是 message.content")
    void chat_returnsMessageContent() {
        server.expect(requestTo(containsString("/api/chat")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        String answer = client.chat(SYSTEM, USER);

        assertEquals("每生每年8000元", answer);
        server.verify();
    }

    @Test
    @DisplayName("请求体：必须带 model、两条消息、stream=false")
    void chat_sendsStreamFalseAndMessages() {
        server.expect(requestTo(containsString("/api/chat")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen2.5:7b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(SYSTEM))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value(USER))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        client.chat(SYSTEM, USER);

        server.verify();
    }
}

package com.example.ragkit.service;

import com.example.ragkit.config.OllamaProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;

@Component
public class OllamaChatClient {

    private final RestClient ollamaChatClient;
    private final String model;

    record ChatMessage(String role, String content) {}
    record ChatRequest(String model, List<ChatMessage> messages, boolean stream) {}

    private static class Message{
        String role;
        String content;
        public String getRole() {return role;}
        public String getContent() {return content;}
    }

    private static class ChatResponse{
        Message message;
        public Message getMessage() {return message;}
    }

    public OllamaChatClient(RestClient ollamaRestClient, OllamaProperties props) {
        this.ollamaChatClient = ollamaRestClient;
        this.model = props.getChatModel();
    }

    /**
     *
     * @param systemPrompt
     * @param userPrompt
     * @return 模型回复的正文，即 message.content */
    public String chat(String systemPrompt, String userPrompt) {
        ChatRequest payload = new ChatRequest(
                model,
                List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userPrompt)
                ),
                false
        );

        ChatResponse response = ollamaChatClient.post()
                .uri("/api/chat")
                .body(payload)
                .retrieve()
                .body(ChatResponse.class);

        return response.getMessage().getContent();
    }
}

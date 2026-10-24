package com.example.ragkit.service;

import com.example.ragkit.config.OllamaProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
public class EmbeddingClient {

    private final RestClient ollamaRestClient;
    private final String model;

    public EmbeddingClient(RestClient ollamaRestClient, OllamaProperties props) {
        this.ollamaRestClient = ollamaRestClient;
        this.model = props.getEmbeddingModel();
    }

    private static class EmbedResponse {
        private List<List<Double>> embeddings;

        public List<List<Double>> getEmbeddings() {
            return embeddings;
        }
    }

    public List<List<Double>> embed(List<String> texts) {

        if(texts == null || texts.isEmpty()){
            return List.of();
        }

        Map<String,Object> payload = new HashMap<>();
        payload.put("model",this.model);
        payload.put("input", texts);

        EmbedResponse response = ollamaRestClient.post()
                .uri("/api/embed")
                .body(payload)
                .retrieve()
                .body(EmbedResponse.class);
        return response.getEmbeddings();
    }
}

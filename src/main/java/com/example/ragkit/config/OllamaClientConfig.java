package com.example.ragkit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 与本地 Ollama 打交道的 HTTP 客户端装配。
 *
 * <p>这里刻意只做一件事：把 baseUrl 和超时在装配阶段固定下来。
 * 换模型厂商（Ollama → 云端 API）时只改这个类和 {@link OllamaProperties}，
 * Service 层完全不知道对面是谁 —— 它只认识注入进来的 RestClient。
 *
 * <p>Ollama 是本机服务，无鉴权，所以这里不加 Authorization 头。
 */
@Configuration
public class OllamaClientConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeout());
        factory.setReadTimeout(props.getReadTimeout());

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

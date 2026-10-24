package com.example.ragkit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 本地 Ollama 服务的连接参数。
 *
 * Ollama 是本机服务，不需要 API Key —— 这是它跟 Dify 这类云端服务的关键区别，
 * 所以这里少了 DifyProperties 里的 apiKey 字段。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
//  Ollama 服务地址
    private String baseUrl;

//  向量模型名
    private String embeddingModel;

//  模型名
    private String chatModel;

//   连接超时
    private Duration connectTimeout = Duration.ofSeconds(5);

//    读取超时
    private Duration readTimeout = Duration.ofSeconds(60);
}

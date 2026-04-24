package com.example.chat.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized OpenRouter settings used by the chat service and HTTP client.
 */
@Data
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterProperties {

    private String apiKey;
    private String baseUrl;
    private String defaultModel;
    private String siteUrl;
    private String appName;
    private Double temperature;
    private Integer maxTokens;
    private Integer maxContextMessages;
    private Duration connectTimeout;
    private Duration readTimeout;
}

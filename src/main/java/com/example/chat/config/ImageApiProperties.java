package com.example.chat.config;

import java.util.List;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Data
@ConfigurationProperties(prefix = "image-api")
public class ImageApiProperties {

    private String apiKey;
    private String baseUrl;
    private String defaultModel;
    private Duration connectTimeout;
    private Duration readTimeout;
    private DataSize maxUploadSize;
    private Integer maxUploadImages;
    private List<String> allowedMimeTypes;
    private Duration rateLimitWindow;
    private Integer rateLimitMaxRequests;
}

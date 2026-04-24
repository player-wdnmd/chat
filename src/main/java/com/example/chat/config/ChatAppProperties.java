package com.example.chat.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chat-app")
public class ChatAppProperties {

    private Limits limits = new Limits();
    private Auth auth = new Auth();
    private Chat chat = new Chat();
    private Image image = new Image();
    private Frontend frontend = new Frontend();

    @Data
    public static class Limits {
        private Integer unlimitedPoints;
        private Integer unlimitedPointsTolerance;
    }

    @Data
    public static class Auth {
        private Integer registerDefaultPoints;
        private Map<String, Integer> redeemCodes = new LinkedHashMap<>();
        private Duration sessionMaxIdle;
        private Duration loginRateLimitWindow;
        private Integer loginRateLimitMaxRequests;
        private Duration registerRateLimitWindow;
        private Integer registerRateLimitMaxRequests;
        private Duration redeemRateLimitWindow;
        private Integer redeemRateLimitMaxRequests;
    }

    @Data
    public static class Chat {
        private Integer costPerRequest;
    }

    @Data
    public static class Image {
        private Integer costPerRequest;
        private Integer historyLimit;
    }

    @Data
    public static class Frontend {
        private String authTokenStorageKey;
        private String systemPrompt;
        private String defaultSkillTemplateLocation;
    }
}

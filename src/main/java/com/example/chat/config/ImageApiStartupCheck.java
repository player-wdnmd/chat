package com.example.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Log4j2
@Component
@RequiredArgsConstructor
public class ImageApiStartupCheck implements ApplicationRunner {

    private final ImageApiProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        boolean configured = StringUtils.hasText(properties.getApiKey()) && StringUtils.hasText(properties.getBaseUrl());
        if (!configured) {
            log.warn("图片能力未完整配置：缺少 IMAGE_API_KEY 或 IMAGE_API_BASE_URL，图片工具页将不可用。");
            return;
        }

        log.info(
                "图片能力配置已加载：baseUrl={}，defaultModel={}，maxUploadBytes={}，maxUploadImages={}，rateLimit={} / {}s",
                properties.getBaseUrl(),
                properties.getDefaultModel(),
                properties.getMaxUploadSize().toBytes(),
                properties.getMaxUploadImages(),
                properties.getRateLimitMaxRequests(),
                properties.getRateLimitWindow().toSeconds()
        );
    }
}

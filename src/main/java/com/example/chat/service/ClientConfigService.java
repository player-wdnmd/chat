package com.example.chat.service;

import com.example.chat.config.ChatAppProperties;
import com.example.chat.model.PublicClientConfigResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientConfigService {

    private final ChatAppProperties properties;
    private final ResourceLoader resourceLoader;

    public PublicClientConfigResponse getPublicConfig() {
        return new PublicClientConfigResponse(
                properties.getFrontend().getAuthTokenStorageKey(),
                properties.getLimits().getUnlimitedPoints(),
                properties.getFrontend().getSystemPrompt(),
                loadDefaultSkillPromptTemplate()
        );
    }

    private String loadDefaultSkillPromptTemplate() {
        Resource resource = resourceLoader.getResource(properties.getFrontend().getDefaultSkillTemplateLocation());
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取默认 Skill 模板失败。", exception);
        }
    }
}

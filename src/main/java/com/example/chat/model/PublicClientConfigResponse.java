package com.example.chat.model;

public record PublicClientConfigResponse(
        String authTokenStorageKey,
        Integer unlimitedPoints,
        String systemPrompt,
        String defaultSkillPromptTemplate
) {
}

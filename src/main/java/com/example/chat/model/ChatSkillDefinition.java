package com.example.chat.model;

public record ChatSkillDefinition(
        String id,
        String name,
        String description,
        String systemPrompt
) {
}

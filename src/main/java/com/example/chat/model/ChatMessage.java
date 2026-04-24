package com.example.chat.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Minimal chat message shape shared by request validation and skill prompt injection.
 */
public record ChatMessage(
        @NotBlank(message = "role 不能为空") String role,
        @NotBlank(message = "content 不能为空") String content
) {
}

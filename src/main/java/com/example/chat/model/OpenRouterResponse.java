package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record OpenRouterResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage
) {
    public record Choice(
            Integer index,
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Message(
            String role,
            JsonNode content
    ) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}

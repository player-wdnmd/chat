package com.example.chat.model;

/**
 * Response returned to the browser for one assistant turn.
 *
 * <p>The frontend uses `remainingPoints` to update local send availability immediately after a
 * successful model call, while the other debug fields remain useful for diagnostics and future UI work.</p>
 */
public record ChatResponse(
        String model,
        String content,
        String requestId,
        Long latencyMs,
        Integer remainingPoints,
        TokenUsage usage
) {
    public record TokenUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
    }
}

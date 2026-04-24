package com.example.chat.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request payload sent by the browser when a user submits one chat turn.
 *
 * <p>The current UI does not expose model switching, so this request only carries:
 * the conversation id for logging, the selected skill id list (normalized to 0..1 item),
 * and the visible message history used to build the OpenRouter request.</p>
 */
public record ChatRequest(
        String conversationId,
        List<String> skillIds,
        @NotEmpty(message = "messages 至少要有一条") List<@Valid ChatMessage> messages
) {
}

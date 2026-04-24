package com.example.chat.model;

import java.util.List;

/**
 * A single conversation persisted in the local state file.
 *
 * <p>The stored shape intentionally stays close to what the UI renders:
 * title/subtitle for the sidebar, one selected skill id, the visible messages,
 * and the last update timestamp used for ordering and timestamps.</p>
 */
public record ChatStateConversation(
        String id,
        String title,
        String subtitle,
        List<String> skillIds,
        List<ChatStateMessage> messages,
        Long updatedAt
) {
}

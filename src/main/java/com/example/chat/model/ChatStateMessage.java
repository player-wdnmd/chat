package com.example.chat.model;

/**
 * Message shape persisted inside one conversation.
 *
 * <p>`pending` and `error` are transient UI hints. The state service normalizes them on load so
 * stale "still loading" placeholders do not survive across restarts.</p>
 */
public record ChatStateMessage(
        String id,
        String role,
        String content,
        Long createdAt,
        Boolean pending,
        String meta,
        Boolean error
) {
}

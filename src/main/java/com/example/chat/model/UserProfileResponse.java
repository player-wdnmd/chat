package com.example.chat.model;

/**
 * Lightweight user profile returned after token verification and after chat requests.
 */
public record UserProfileResponse(
        String accountName,
        Integer points
) {
}

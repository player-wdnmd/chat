package com.example.chat.model;

/**
 * Authenticated user context resolved from the bearer token on each API request.
 */
public record AuthenticatedUser(
        Long id,
        String accountName,
        Integer points
) {
}

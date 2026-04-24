package com.example.chat.model;

/**
 * Returned after register/login so the frontend can persist the bearer token
 * and immediately display the current account summary.
 */
public record AuthResponse(
        String token,
        String accountName,
        Integer points
) {
}

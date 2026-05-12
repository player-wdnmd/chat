package com.example.chat.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OpenRouterException extends RuntimeException {

    private final HttpStatus status;
    private final boolean retryable;

    public OpenRouterException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, false);
    }

    public OpenRouterException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, true, cause);
    }

    public OpenRouterException(String message, HttpStatus status, boolean retryable) {
        super(message);
        this.status = status;
        this.retryable = retryable;
    }

    public OpenRouterException(String message, HttpStatus status, boolean retryable, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryable = retryable;
    }
}

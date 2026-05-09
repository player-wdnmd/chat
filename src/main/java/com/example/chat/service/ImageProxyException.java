package com.example.chat.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ImageProxyException extends RuntimeException {

    private final HttpStatus status;

    public ImageProxyException(String message) {
        this(message, HttpStatus.BAD_GATEWAY);
    }

    public ImageProxyException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, cause);
    }

    public ImageProxyException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public ImageProxyException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}

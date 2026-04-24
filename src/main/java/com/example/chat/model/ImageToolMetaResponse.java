package com.example.chat.model;

import java.util.List;

public record ImageToolMetaResponse(
        boolean configured,
        int operationCost,
        long maxUploadBytes,
        int maxUploadImages,
        List<String> allowedMimeTypes,
        int rateLimitMaxRequests,
        long rateLimitWindowSeconds
) {
}

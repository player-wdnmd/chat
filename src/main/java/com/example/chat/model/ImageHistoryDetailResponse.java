package com.example.chat.model;

import java.time.LocalDateTime;

public record ImageHistoryDetailResponse(
        Long id,
        String operationType,
        String model,
        String prompt,
        String imageDataUrl,
        String resultInfo,
        String responseJson,
        LocalDateTime createdAt
) {
}

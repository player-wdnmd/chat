package com.example.chat.model;

import java.time.LocalDateTime;

public record ImageHistorySummaryResponse(
        Long id,
        String operationType,
        String model,
        String prompt,
        String imageDataUrl,
        String resultInfo,
        LocalDateTime createdAt
) {
}

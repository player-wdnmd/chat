package com.example.chat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageGenerationRequest(
        String model,
        @NotBlank(message = "prompt 不能为空")
        @Size(max = 4000, message = "prompt 不能超过 4000 个字符")
        String prompt
) {
}

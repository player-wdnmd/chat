package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImageChatCompletionRequest(
        String model,
        String role,
        List<@Valid ContentPart> content,
        List<@Valid Message> messages
) {
    public record Message(
            @NotBlank(message = "message.role 不能为空") String role,
            List<@Valid ContentPart> content
    ) {
    }

    public record ContentPart(
            @NotBlank(message = "content.type 不能为空") String type,
            @Size(max = 4000, message = "text 不能超过 4000 个字符") String text,
            @JsonProperty("image_url")
            ImageUrl imageUrl
    ) {
    }

    public record ImageUrl(String url) {
    }
}

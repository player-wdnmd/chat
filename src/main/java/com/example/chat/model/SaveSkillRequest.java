package com.example.chat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload used by the skill management page to create a custom skill.
 */
public record SaveSkillRequest(
        @NotBlank(message = "skillName 不能为空")
        @Size(max = 128, message = "skillName 不能超过 128 个字符")
        @Pattern(regexp = "^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5\\s]+$", message = "skillName 只允许中英文、数字、空格、下划线和横线")
        String skillName,
        @Size(max = 255, message = "skillDescription 不能超过 255 个字符")
        String skillDescription,
        @NotBlank(message = "systemPrompt 不能为空")
        String systemPrompt
) {
}

package com.example.chat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Users only need a custom account name and password.
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度需要在 3 到 64 个字符之间")
        @Pattern(regexp = "^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$", message = "用户名 只允许中英文、数字、下划线和横线")
        String accountName,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度需要在 6 到 128 个字符之间")
        String password
) {
}

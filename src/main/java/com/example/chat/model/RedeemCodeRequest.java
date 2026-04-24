package com.example.chat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 兑换码提交请求。
 */
public record RedeemCodeRequest(
        @NotBlank(message = "兑换码不能为空")
        @Size(max = 32, message = "兑换码长度不能超过 32 个字符")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "兑换码只允许字母和数字")
        String redeemCode
) {
}

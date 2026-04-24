package com.example.chat.controller;

import com.example.chat.model.AuthResponse;
import com.example.chat.model.AuthenticatedUser;
import com.example.chat.model.LoginRequest;
import com.example.chat.model.RedeemCodeRequest;
import com.example.chat.model.RegisterRequest;
import com.example.chat.model.UserProfileResponse;
import com.example.chat.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册、登录和“当前用户信息”接口。
 */
@Log4j2
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(HttpServletRequest servletRequest, @Valid @RequestBody RegisterRequest request) {
        log.info("收到注册请求：accountName={}", request.accountName());
        return authService.register(request, extractClientIp(servletRequest));
    }

    @PostMapping("/login")
    public AuthResponse login(HttpServletRequest servletRequest, @Valid @RequestBody LoginRequest request) {
        log.info("收到登录请求：accountName={}", request.accountName());
        return authService.login(request, extractClientIp(servletRequest));
    }

    @GetMapping("/me")
    public UserProfileResponse me(HttpServletRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        UserProfileResponse profile = authService.getProfile(user);
        log.info("返回当前用户信息：userId={}，accountName={}，points={}", user.id(), profile.accountName(), profile.points());
        return profile;
    }

    @PostMapping("/redeem")
    public UserProfileResponse redeem(HttpServletRequest request, @Valid @RequestBody RedeemCodeRequest redeemCodeRequest) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("收到兑换码请求：userId={}，redeemCodeLength={}", user.id(), redeemCodeRequest.redeemCode() == null ? 0 : redeemCodeRequest.redeemCode().length());
        return authService.redeemCode(user, redeemCodeRequest, extractClientIp(request));
    }

    @DeleteMapping("/session")
    public void logout(HttpServletRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        authService.logout(request);
        log.info("用户已退出登录：userId={}", user.id());
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

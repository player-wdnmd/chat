package com.example.chat.config;

import com.example.chat.controller.ApiError;
import com.example.chat.model.AuthenticatedUser;
import com.example.chat.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 对受保护 API 统一执行 Bearer Token 鉴权。
 *
 * <p>只有注册和登录接口允许匿名访问，其余 `/api/*` 都必须携带合法 token。
 * 鉴权成功后，过滤器会把当前用户挂到 request attribute，
 * 供后续控制器和服务层直接读取。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/config/public"
    );

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || PUBLIC_API_PATHS.contains(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = extractBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "未登录，请先登录。");
            return;
        }

        AuthenticatedUser authenticatedUser = authService.resolveByToken(token).orElse(null);
        if (authenticatedUser == null) {
            writeUnauthorized(response, "登录已失效，请重新登录。");
            return;
        }

        request.setAttribute(AuthService.AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiError(message, Map.of()));
    }
}

package com.example.chat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Log4j2
@Component
public class RequestTracingFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID = "requestId";

    // 静态资源请求通常没有排查价值，因此这里只给 /api/* 请求加 requestId，
    // 让日志足够聚焦，避免页面资源加载把控制台刷满。
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 如果上游已经带了 X-Request-Id，就直接复用，这样反向代理、网关、客户端日志
        // 可以和服务端日志串起来看；如果没有，再由当前服务生成一个短 id。
        String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
                .filter(StringUtils::hasText)
                .orElseGet(this::generateRequestId);

        MDC.put(REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info(
                    "请求完成：method={}，uri={}，status={}，耗时={} ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed
            );
            MDC.remove(REQUEST_ID);
        }
    }

    // 本地开发环境下，短 requestId 比完整 UUID 更容易肉眼追踪。
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

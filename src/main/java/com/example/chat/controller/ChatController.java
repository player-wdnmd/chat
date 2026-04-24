package com.example.chat.controller;

import com.example.chat.model.AuthenticatedUser;
import com.example.chat.model.ChatRequest;
import com.example.chat.model.ChatResponse;
import com.example.chat.service.AuthService;
import com.example.chat.service.OpenRouterChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AuthService authService;
    private final OpenRouterChatService chatService;

    /**
     * 接收前端提交的一轮对话请求，并把真正的提示词组装与模型调用工作交给
     * {@link OpenRouterChatService}。
     *
     * <p>这里的入口日志只保留最关键的上下文：
     * 会话 id、消息条数、技能条数。
     * 排查问题时通常先看这三项，就能快速判断是前端状态没带对，
     * 还是后续模型调用链路出了问题。</p>
     */
    @PostMapping
    public ChatResponse chat(HttpServletRequest servletRequest, @Valid @RequestBody ChatRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(servletRequest);
        log.info(
                "收到聊天请求：userId={}，conversationId={}，messageCount={}，skillCount={}",
                user.id(),
                request.conversationId(),
                request.messages().size(),
                request.skillIds() == null ? 0 : request.skillIds().size()
        );
        return chatService.chat(user, request);
    }
}

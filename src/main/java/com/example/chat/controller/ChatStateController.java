package com.example.chat.controller;

import com.example.chat.model.AuthenticatedUser;
import com.example.chat.model.ChatState;
import com.example.chat.service.AuthService;
import com.example.chat.service.ChatStateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/api/state")
@RequiredArgsConstructor
public class ChatStateController {

    private final AuthService authService;
    private final ChatStateService chatStateService;

    /**
     * 返回当前本地状态文件中的完整会话树，用于前端初始化。
     *
     * <p>这里的日志重点是会话数量和当前激活会话 id，
    * 因为这两个字段最能反映状态文件是否被正确读取、
     * 以及前端初始化时会落到哪一个会话上。</p>
     */
    @GetMapping
    public ChatState getState(HttpServletRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        ChatState state = chatStateService.load(user.id());
        log.info(
                "返回聊天状态：userId={}，conversationCount={}，activeConversationId={}",
                user.id(),
                state.conversations() == null ? 0 : state.conversations().size(),
                state.activeConversationId()
        );
        return state;
    }

    /**
     * 用前端最新快照覆盖保存整个状态文档。
     *
     * <p>接口级日志主要用于判断：
     * 当前是不是频繁保存、
     * 保存的会话数量是否异常、
     * 以及前端当前认为哪个会话是激活态。</p>
     */
    @PutMapping
    public ChatState saveState(HttpServletRequest request, @Valid @RequestBody ChatState state) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info(
                "收到状态保存请求：userId={}，conversationCount={}，activeConversationId={}",
                user.id(),
                state.conversations() == null ? 0 : state.conversations().size(),
                state.activeConversationId()
        );
        return chatStateService.save(user.id(), state);
    }
}

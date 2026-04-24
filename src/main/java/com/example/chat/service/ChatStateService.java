package com.example.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.chat.entity.ChatUserState;
import com.example.chat.mapper.ChatUserStateMapper;
import com.example.chat.model.ChatState;
import com.example.chat.model.ChatStateConversation;
import com.example.chat.model.ChatStateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 把每个用户的完整会话树序列化后保存到 `chat_user_state` 表。
 */
@Service
@RequiredArgsConstructor
public class ChatStateService {

    private static final Logger log = LoggerFactory.getLogger(ChatStateService.class);

    private final ChatUserStateMapper chatUserStateMapper;
    private final ObjectMapper objectMapper;

    /**
     * 读取一个用户的聊天状态。
     *
     * <p>如果该用户还没有保存过任何状态，则返回一个默认空白会话。</p>
     */
    public synchronized ChatState load(Long userId) {
        ChatUserState userState = chatUserStateMapper.selectById(userId);
        if (userState == null || !StringUtils.hasText(userState.getStateJson())) {
            log.info("用户尚无聊天状态，返回默认空白状态：userId={}", userId);
            return createDefaultState();
        }

        try {
            ChatState normalizedState = normalizeState(objectMapper.readValue(userState.getStateJson(), ChatState.class));
            log.info(
                    "用户聊天状态读取成功：userId={}，conversationCount={}，activeConversationId={}",
                    userId,
                    normalizedState.conversations().size(),
                    normalizedState.activeConversationId()
            );
            return normalizedState;
        } catch (JsonProcessingException exception) {
            log.warn("用户聊天状态解析失败，回退到默认空白状态：userId={}", userId, exception);
            return createDefaultState();
        }
    }

    /**
     * 保存一个用户的完整聊天状态。
     *
     * <p>这里使用“先查后改”的 upsert 流程，不再手写 SQL。</p>
     */
    @Transactional
    public synchronized ChatState save(Long userId, ChatState requestedState) {
        ChatState normalizedState = normalizeState(requestedState);
        try {
            String stateJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalizedState);
            ChatUserState existing = chatUserStateMapper.selectById(userId);
            if (existing == null) {
                ChatUserState userState = new ChatUserState();
                userState.setUserId(userId);
                userState.setStateJson(stateJson);
                chatUserStateMapper.insert(userState);
            } else {
                existing.setStateJson(stateJson);
                chatUserStateMapper.updateById(existing);
            }

            log.info(
                    "用户聊天状态保存成功：userId={}，conversationCount={}，activeConversationId={}",
                    userId,
                    normalizedState.conversations().size(),
                    normalizedState.activeConversationId()
            );
            return normalizedState;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("聊天记录格式不正确，无法保存。", exception);
        }
    }

    private ChatState normalizeState(ChatState rawState) {
        if (rawState == null || rawState.conversations() == null || rawState.conversations().isEmpty()) {
            return createDefaultState();
        }

        List<ChatStateConversation> conversations = rawState.conversations().stream()
                .map(this::normalizeConversation)
                .toList();
        String activeConversationId = conversations.stream()
                .anyMatch(item -> item.id().equals(rawState.activeConversationId()))
                ? rawState.activeConversationId()
                : conversations.getFirst().id();

        return new ChatState(
                conversations,
                activeConversationId,
                StringUtils.hasText(rawState.lastUsage()) ? rawState.lastUsage() : "尚未发送消息"
        );
    }

    private ChatStateConversation normalizeConversation(ChatStateConversation conversation) {
        List<ChatStateMessage> messages = conversation.messages() == null
                ? List.of()
                : conversation.messages().stream().map(this::normalizeMessage).toList();
        List<String> skillIds = normalizeSkillIds(conversation.skillIds());

        return new ChatStateConversation(
                StringUtils.hasText(conversation.id()) ? conversation.id() : UUID.randomUUID().toString(),
                StringUtils.hasText(conversation.title()) ? conversation.title() : "未命名对话",
                StringUtils.hasText(conversation.subtitle()) ? conversation.subtitle() : "等待第一条消息",
                skillIds,
                messages,
                conversation.updatedAt() != null ? conversation.updatedAt() : System.currentTimeMillis()
        );
    }

    private ChatStateMessage normalizeMessage(ChatStateMessage message) {
        return new ChatStateMessage(
                StringUtils.hasText(message.id()) ? message.id() : UUID.randomUUID().toString(),
                StringUtils.hasText(message.role()) ? message.role() : "assistant",
                StringUtils.hasText(message.content()) ? message.content() : "",
                message.createdAt() != null ? message.createdAt() : System.currentTimeMillis(),
                Boolean.TRUE.equals(message.pending()) ? Boolean.FALSE : message.pending(),
                StringUtils.hasText(message.meta()) ? message.meta() : null,
                Boolean.TRUE.equals(message.pending()) ? Boolean.TRUE : message.error()
        );
    }

    private ChatState createDefaultState() {
        long now = System.currentTimeMillis();
        String conversationId = UUID.randomUUID().toString();
        ChatStateConversation firstConversation = new ChatStateConversation(
                conversationId,
                "会话 1",
                "等待第一条消息",
                List.of(),
                new ArrayList<>(),
                now
        );
        return new ChatState(List.of(firstConversation), conversationId, "尚未发送消息");
    }

    private List<String> normalizeSkillIds(List<String> rawSkillIds) {
        if (rawSkillIds == null || rawSkillIds.isEmpty()) {
            return List.of();
        }

        List<String> normalized = rawSkillIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of() : List.of(normalized.getFirst());
    }
}

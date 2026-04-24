package com.example.chat.model;

import java.util.List;

/**
 * 顶层聊天状态文档。
 *
 * <p>当前版本里，这个结构会按用户序列化后存进数据库表 `chat_user_state.state_json`，
 * 前端初始化时再整块读回，因此每个账号都有自己独立的聊天历史。</p>
 */
public record ChatState(
        List<ChatStateConversation> conversations,
        String activeConversationId,
        String lastUsage
) {
}

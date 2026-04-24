package com.example.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对应数据库表：`chat_user_state`
 *
 * <p>按用户保存完整聊天状态 JSON。
 * 当前设计是一位用户只保留一份顶层状态文档，由 `user_id` 直接作为主键。</p>
 */
@Data
@TableName("chat_user_state")
public class ChatUserState {

    /**
     * 用户 ID，同时也是主键。
     *
     * <p>这样可以保证一个用户只对应一条状态记录。</p>
     */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /**
     * 完整会话树 JSON。
     *
     * <p>前端的 conversations / activeConversationId / lastUsage 会整体序列化到这里。</p>
     */
    private String stateJson;

    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}

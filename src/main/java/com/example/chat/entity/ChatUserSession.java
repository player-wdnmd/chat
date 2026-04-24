package com.example.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对应数据库表：`chat_user_session`
 *
 * <p>保存 bearer token 的哈希值与所属用户关系，用于接口鉴权。</p>
 */
@Data
@TableName("chat_user_session")
public class ChatUserSession {

    /**
     * 登录会话主键，自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID，关联 `chat_user.id`。
     */
    private Long userId;

    /**
     * Bearer Token 的 SHA-256 哈希值。
     *
     * <p>只存哈希，不存明文 token，降低会话泄露风险。</p>
     */
    private String tokenHash;

    /**
     * 会话创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最近一次使用时间。
     *
     * <p>后续如果要做“长期未使用会话清理”，会依赖这个字段。</p>
     */
    private LocalDateTime lastUsedAt;
}

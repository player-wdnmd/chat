package com.example.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对应数据库表：`chat_user`
 *
 * <p>保存账号、密码哈希与积分余额，是整套账号体系的主表。</p>
 */
@Data
@TableName("chat_user")
public class ChatUser {

    /**
     * 用户主键，自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户自定义账号名。
     *
     * <p>当前项目不使用邮箱/手机号注册，登录唯一凭证就是这个账号名。</p>
     */
    private String accountName;

    /**
     * BCrypt 密码哈希。
     *
     * <p>数据库中不保存明文密码，登录时通过 BCrypt 校验。</p>
     */
    private String passwordHash;

    /**
     * 当前积分余额。
     *
     * <p>每次发送一轮聊天请求会消耗 1 积分。
     * 约定值 `2147483647` 可视为“无限积分”。</p>
     */
    private Integer points;

    /**
     * 账号创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 账号最后更新时间。
     */
    private LocalDateTime updatedAt;
}

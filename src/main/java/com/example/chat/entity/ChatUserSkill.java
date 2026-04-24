package com.example.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对应数据库表：`chat_user_skill`
 *
 * <p>保存用户自定义 skill，以及管理员预置 skill。
 * 每条记录天然绑定一个用户，因此不同账号之间的技能彼此隔离。</p>
 */
@Data
@TableName("chat_user_skill")
public class ChatUserSkill {

    /**
     * 技能主键，自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID，关联 `chat_user.id`。
     */
    private Long userId;

    /**
     * 技能名称。
     *
     * <p>前端聊天页下拉框和技能管理页列表都会直接展示这个字段。</p>
     */
    private String skillName;

    /**
     * 技能简介。
     *
     * <p>用于帮助用户在下拉框和管理页快速区分 skill。</p>
     */
    private String skillDescription;

    /**
     * 技能提示词正文。
     *
     * <p>发送消息时，后端会把它包装成一条 system prompt 注入模型上下文。</p>
     */
    private String systemPrompt;

    /**
     * 技能创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 技能最后更新时间。
     */
    private LocalDateTime updatedAt;
}

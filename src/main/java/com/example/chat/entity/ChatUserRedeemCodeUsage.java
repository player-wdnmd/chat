package com.example.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对应数据库表：`chat_user_redeem_code_usage`
 *
 * <p>记录每个用户已经使用过哪些兑换码，避免重复兑换。</p>
 */
@Data
@TableName("chat_user_redeem_code_usage")
public class ChatUserRedeemCodeUsage {

    /**
     * 主键，自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID。
     */
    private Long userId;

    /**
     * 已兑换的兑换码。
     */
    private String redeemCode;

    /**
     * 本次兑换实际发放的积分值。
     */
    private Integer pointsGranted;

    /**
     * 兑换时间。
     */
    private LocalDateTime createdAt;
}

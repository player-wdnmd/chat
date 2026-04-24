package com.example.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_user_image_history")
public class ChatUserImageHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String operationType;

    private String model;

    private String prompt;

    private String imageDataUrl;

    private String resultInfo;

    private String responseJson;

    private LocalDateTime createdAt;
}

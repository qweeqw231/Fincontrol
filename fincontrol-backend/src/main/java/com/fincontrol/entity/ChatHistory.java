package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * chat_history 表实体（[db-schema.sql §4](#)）。
 *
 * <pre>
 * id                BIGINT       PK AI
 * user_id           BIGINT       NOT NULL DEFAULT 1
 * conversation_id   VARCHAR(50)  NOT NULL  (UUID)
 * role              VARCHAR(20)  NOT NULL  (user|assistant)
 * content           TEXT         NOT NULL
 * created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
 * conversation_type VARCHAR(30)  NOT NULL DEFAULT 'ai_assistant' (ai_assistant|screenshot_parse)
 * </pre>
 */
@Data
@TableName("chat_history")
public class ChatHistory {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static final String CONVERSATION_TYPE_AI_ASSISTANT = "ai_assistant";
    public static final String CONVERSATION_TYPE_SCREENSHOT_PARSE = "screenshot_parse";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String conversationId;

    private String role;

    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private String conversationType;
}

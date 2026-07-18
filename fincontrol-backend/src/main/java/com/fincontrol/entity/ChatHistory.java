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
 * id                  BIGINT       PK AI
 * user_id             BIGINT       NOT NULL DEFAULT 1
 * conversation_id     VARCHAR(50)  NOT NULL  (UUID)
 * role                VARCHAR(20)  NOT NULL  (user|assistant)
 * content             TEXT         NOT NULL
 * created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
 * conversation_type   VARCHAR(30)  NOT NULL DEFAULT 'ai_assistant' (ai_assistant|screenshot_parse)
 * used_provider       VARCHAR(20)  NULL     (1a.8: minimax/doubao/deepseek)
 * fallback_triggered  TINYINT(1)   NOT NULL DEFAULT 0  (1a.8)
 * </pre>
 */
@Data
@TableName("chat_history")
public class ChatHistory {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static final String CONVERSATION_TYPE_AI_ASSISTANT = "ai_assistant";
    public static final String CONVERSATION_TYPE_SCREENSHOT_PARSE = "screenshot_parse";

    // 1a.8：AI provider 标识（与 chat_history.used_provider 对应）
    public static final String PROVIDER_MINIMAX = "minimax";
    public static final String PROVIDER_DOUBAO = "doubao";
    public static final String PROVIDER_DEEPSEEK = "deepseek";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String conversationId;

    private String role;

    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private String conversationType;

    /** 1a.8：本响应实际使用的 provider（minimax/doubao/deepseek），user 行与失败行为 null。 */
    private String usedProvider;

    /** 1a.8：本响应是否走了 fallback（0=主路径 / 1=已 fallback），user 行强制 0。 */
    private Boolean fallbackTriggered;
}

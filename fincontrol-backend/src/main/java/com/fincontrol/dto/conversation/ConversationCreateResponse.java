package com.fincontrol.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 1a.21 /api/conversations 创建空对话响应（[api-contract.md §8.4](#)）。
 *
 * <p>Phase 1 行为：仅生成 UUID 并返回，不插库（首条 user 消息在 /api/chat/send 时才落）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationCreateResponse {

    private String conversationId;

    /** "screenshot_parse" | "ai_assistant" */
    private String type;

    private LocalDateTime createdAt;
}
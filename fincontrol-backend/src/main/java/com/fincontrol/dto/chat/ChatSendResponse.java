package com.fincontrol.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1a.18 /api/chat/send 响应体（[api-contract.md §8.1](#)）。
 *
 * <p>包含：
 * <ul>
 *   <li>{@code conversationId} — 新对话时为后端生成的 UUID；继续对话时为请求的 conversationId</li>
 *   <li>{@code userMessage} — 用户消息（含 createdAt）</li>
 *   <li>{@code assistantMessage} — AI 回复（含 routedTo + promptVersion）</li>
 *   <li>{@code intentClassification} — 意图分类结果 + 延迟（毫秒）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendResponse {

    private String conversationId;
    private ChatMessageDto userMessage;
    private ChatMessageDto assistantMessage;
    private IntentClassificationDto intentClassification;
}
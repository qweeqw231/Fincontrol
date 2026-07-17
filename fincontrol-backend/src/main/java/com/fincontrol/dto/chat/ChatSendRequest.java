package com.fincontrol.dto.chat;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1a.18 /api/chat/send 请求体（[api-contract.md §8.1](#)）。
 *
 * <p>字段：
 * <ul>
 *   <li>{@code conversationId} — 可选；空时由后端生成 UUID 触发"新建对话"</li>
 *   <li>{@code message} — 必填，用户明文</li>
 *   <li>{@code userId} — 可选，缺省回退到 {@code X-User-Id} header（与既有 controllers 一致）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class ChatSendRequest {

    private String conversationId;
    private String message;
    private Long userId;
}
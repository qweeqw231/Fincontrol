package com.fincontrol.dto.conversation;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1a.21 /api/conversations 创建空对话请求（[api-contract.md §8.4](#)）。
 *
 * <p>Phase 1 字段：
 * <ul>
 *   <li>{@code type} — 必填，"screenshot_parse" | "ai_assistant"</li>
 *   <li>{@code userId} — 可选，缺省回退到 {@code X-User-Id} header</li>
 * </ul>
 *
 * <p>按 [2026-07-17_phase1a6-work-plan.md §1 G4](#) 决策：仅返回 conversationId，不立即插库。
 */
@Data
@NoArgsConstructor
public class ConversationCreateRequest {

    private String type;
    private Long userId;
}
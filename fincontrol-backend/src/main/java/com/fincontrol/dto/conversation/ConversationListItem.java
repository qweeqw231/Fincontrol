package com.fincontrol.dto.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 1a.19 /api/conversations 列表项（[api-contract.md §8.2](#) Phase 1 基础字段版）。
 *
 * <p>按 [2026-07-17_phase1a6-work-plan.md §1 G5](#) 决策：
 * <ul>
 *   <li>Phase 1 仅返回 4 个基础字段：conversationId / type / createdAt / lastMessageAt</li>
 *   <li>{@code fundCount} / {@code snapshotDate} / {@code status} 派生字段留 P2</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationListItem {

    private String conversationId;

    /** "screenshot_parse" | "ai_assistant" */
    private String type;

    /** 该对话首条消息时间（MIN created_at） */
    private LocalDateTime createdAt;

    /** 该对话最后一条消息时间（MAX created_at） */
    private LocalDateTime lastMessageAt;
}
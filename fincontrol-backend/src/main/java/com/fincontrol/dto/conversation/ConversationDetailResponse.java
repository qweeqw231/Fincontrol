package com.fincontrol.dto.conversation;

import com.fincontrol.dto.chat.ChatMessageDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 1a.20 /api/conversations/{id} 详情响应（[api-contract.md §8.3](#)）。
 *
 * <p>复用 {@link ChatMessageDto} 表达 message，与 /api/chat/send 响应字段对齐。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailResponse {

    private String conversationId;

    /** "screenshot_parse" | "ai_assistant"（从首条消息的 conversationType 字段推断） */
    private String type;

    private List<ChatMessageDto> messages;
}
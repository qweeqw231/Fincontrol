package com.fincontrol.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 1a.18 聊天消息 DTO（[api-contract.md §8.1 / §8.3](#)）。
 *
 * <p>通用结构，同时用于：
 * <ul>
 *   <li>{@code /api/chat/send} 响应中的 userMessage / assistantMessage</li>
 *   <li>{@code /api/conversations/{id}} 响应中的 messages[]</li>
 * </ul>
 *
 * <p>{@code routedTo} + {@code promptVersion} 仅 assistant 消息有意义（垃圾回路 assistant 也只填 routedTo 不填 promptVersion）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageDto {

    /** "user" | "assistant" */
    private String role;

    private String content;

    private LocalDateTime createdAt;

    /** "main_loop" | "garbage_loop"，仅 assistant 消息 */
    private String routedTo;

    /** "ai_assistant v1.0"，仅 main_loop 的 assistant 消息 */
    private String promptVersion;
}
package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.chat.ChatSendRequest;
import com.fincontrol.dto.chat.ChatSendResponse;
import com.fincontrol.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1a.18 /api/chat/send REST 端点（[api-contract.md §8.1](#)，[P0-3.5] [P0-3.6]）。
 *
 * <p>userId 来源：{@code X-User-Id} header（默认 1）；{@code ChatSendRequest.userId} 字段
 * 若提供则覆盖 header，便于压测 / 多用户场景。
 *
 * <p>错误码由 {@code GlobalExceptionHandler} 统一映射（1001/2001/3001/3002/5001 等）。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 1a.18 发送消息。
     */
    @PostMapping("/send")
    public ApiResponse<ChatSendResponse> send(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long headerUserId,
            @RequestBody ChatSendRequest req) {
        if (req == null) {
            req = new ChatSendRequest();
        }
        Long userId = req.getUserId() != null ? req.getUserId() : headerUserId;
        log.info("1a.6 chat.send: userId={} message.len={}",
                userId, req.getMessage() == null ? 0 : req.getMessage().length());
        return ApiResponse.success(chatService.send(userId, req));
    }
}
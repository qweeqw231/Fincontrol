package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.conversation.ConversationCreateRequest;
import com.fincontrol.dto.conversation.ConversationCreateResponse;
import com.fincontrol.dto.conversation.ConversationDeleteResponse;
import com.fincontrol.dto.conversation.ConversationDetailResponse;
import com.fincontrol.dto.conversation.ConversationListResponse;
import com.fincontrol.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1a.19-1a.22 /api/conversations[*] REST 端点（[api-contract.md §8.2-§8.5](#)）。
 *
 * <p>userId 来源：{@code X-User-Id} header（默认 1）；{@code request.userId} 字段
 * 若提供则覆盖 header（与既有 controllers 一致）。
 *
 * <p>错误码：1001 参数 / 2001 找不到 / 5001 server（由 {@code GlobalExceptionHandler} 映射 HTTP）。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 1a.19 列表（可选 type 过滤 + 分页）。
     */
    @GetMapping
    public ApiResponse<ConversationListResponse> list(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        log.info("1a.6 conv.list: userId={} type={} page={} pageSize={}", userId, type, page, pageSize);
        return ApiResponse.success(conversationService.list(userId, type, page, pageSize));
    }

    /**
     * 1a.20 详情。
     */
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> get(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @PathVariable("conversationId") String conversationId) {
        log.info("1a.6 conv.get: userId={} conversationId={}", userId, conversationId);
        return ApiResponse.success(conversationService.get(userId, conversationId));
    }

    /**
     * 1a.21 创建空对话（仅返 conversationId，不插库）。
     */
    @PostMapping
    public ApiResponse<ConversationCreateResponse> create(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long headerUserId,
            @RequestBody ConversationCreateRequest req) {
        if (req == null) {
            req = new ConversationCreateRequest();
        }
        Long userId = req.getUserId() != null ? req.getUserId() : headerUserId;
        log.info("1a.6 conv.create: userId={} type={}", userId, req.getType());
        return ApiResponse.success(conversationService.create(userId, req));
    }

    /**
     * 1a.22 物理删除 + 返回 deletedMessageCount。
     */
    @DeleteMapping("/{conversationId}")
    public ApiResponse<ConversationDeleteResponse> delete(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @PathVariable("conversationId") String conversationId) {
        log.info("1a.6 conv.delete: userId={} conversationId={}", userId, conversationId);
        return ApiResponse.success(conversationService.delete(userId, conversationId));
    }
}
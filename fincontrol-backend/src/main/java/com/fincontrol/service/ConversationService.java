package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.chat.ChatMessageDto;
import com.fincontrol.dto.conversation.ConversationCreateRequest;
import com.fincontrol.dto.conversation.ConversationCreateResponse;
import com.fincontrol.dto.conversation.ConversationDeleteResponse;
import com.fincontrol.dto.conversation.ConversationDetailResponse;
import com.fincontrol.dto.conversation.ConversationListItem;
import com.fincontrol.dto.conversation.ConversationListResponse;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 1a.19-1a.22 对话 CRUD 业务编排（[api-contract.md §8.2-§8.5](#)）。
 *
 * <p>5 个端点：
 * <ul>
 *   <li>{@link #list} — 1a.19</li>
 *   <li>{@link #get} — 1a.20</li>
 *   <li>{@link #create} — 1a.21</li>
 *   <li>{@link #delete} — 1a.22</li>
 * </ul>
 *
 * <p>数据来源：{@code chat_history}（无 conversation_meta 表）。
 * 列表靠 {@code GROUP BY conversation_id} 派生（[work-plan G5](#) 决策 P2 字段不在 Phase 1 范围）。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    public static final String TYPE_AI_ASSISTANT = "ai_assistant";
    public static final String TYPE_SCREENSHOT_PARSE = "screenshot_parse";

    private final ChatHistoryMapper chatHistoryMapper;

    public ConversationService(ChatHistoryMapper chatHistoryMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
    }

    // ========================================================================
    // 1a.19 list
    // ========================================================================

    /**
     * 列对话。{@code type=null} 不过滤；{@code page<1} → 5001；{@code pageSize>100} → 100。
     */
    public ConversationListResponse list(Long userId, String type, Integer page, Integer pageSize) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int ps = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : pageSize;
        if (ps > MAX_PAGE_SIZE) ps = MAX_PAGE_SIZE;
        String normalizedType = normalizeType(type);

        long total = chatHistoryMapper.countConversations(userId, normalizedType);
        int offset = (p - 1) * ps;
        List<Map<String, Object>> rows = chatHistoryMapper.selectConversationList(
                userId, normalizedType, ps, offset);
        List<ConversationListItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(toListItem(row));
        }
        log.info("1a.6 conv.list: userId={} type={} page={} pageSize={} total={} returned={}",
                userId, normalizedType, p, ps, total, items.size());
        return ConversationListResponse.builder()
                .items(items)
                .total(total)
                .page(p)
                .pageSize(ps)
                .build();
    }

    private ConversationListItem toListItem(Map<String, Object> row) {
        return ConversationListItem.builder()
                .conversationId(asString(row.get("conversationId")))
                .type(asString(row.get("conversationType")))
                .createdAt(asLocalDateTime(row.get("createdAt")))
                .lastMessageAt(asLocalDateTime(row.get("lastMessageAt")))
                .build();
    }

    // ========================================================================
    // 1a.20 get
    // ========================================================================

    /**
     * 取对话详情：消息按 created_at asc。
     * <p>空对话（首次 POST /conversations 后尚未 send）→ 抛 2001。
     */
    public ConversationDetailResponse get(Long userId, String conversationId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND, "conversationId 必填");
        }
        List<ChatHistory> msgs = chatHistoryMapper.selectByConversationIdOrderByCreatedAt(conversationId);
        if (msgs == null || msgs.isEmpty()) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND, "对话不存在: " + conversationId);
        }
        // 简单 user 隔离：消息中至少一条 user_id == userId
        boolean owned = msgs.stream().anyMatch(m -> userId.equals(m.getUserId()));
        if (!owned) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND, "对话不存在: " + conversationId);
        }
        String type = msgs.get(0).getConversationType();
        List<ChatMessageDto> messageDtos = new ArrayList<>(msgs.size());
        for (ChatHistory m : msgs) {
            messageDtos.add(ChatMessageDto.builder()
                    .role(m.getRole())
                    .content(m.getContent())
                    .createdAt(m.getCreatedAt())
                    .build());
        }
        return ConversationDetailResponse.builder()
                .conversationId(conversationId)
                .type(type)
                .messages(messageDtos)
                .build();
    }

    // ========================================================================
    // 1a.21 create
    // ========================================================================

    /**
     * 创建空对话：仅生成 UUID 并返回，**不立即插库**（[work-plan G4](#) 决策）。
     * <p>首条 user 消息在 /api/chat/send 时才落 chat_history。
     */
    public ConversationCreateResponse create(Long userId, ConversationCreateRequest req) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "请求体为空");
        }
        String type = normalizeType(req.getType());
        if (!TYPE_AI_ASSISTANT.equals(type) && !TYPE_SCREENSHOT_PARSE.equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE,
                    "type 必须是 'ai_assistant' 或 'screenshot_parse'");
        }
        String conversationId = "conv-" + UUID.randomUUID();
        log.info("1a.6 conv.create: userId={} type={} conversationId={}", userId, type, conversationId);
        return ConversationCreateResponse.builder()
                .conversationId(conversationId)
                .type(type)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ========================================================================
    // 1a.22 delete
    // ========================================================================

    /**
     * 物理删除某 conversation 的全部消息。
     * <p>返回删除行数；空对话也返 0（不抛错）。
     */
    public ConversationDeleteResponse delete(Long userId, String conversationId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND, "conversationId 必填");
        }
        int deleted = chatHistoryMapper.deleteByConversationId(conversationId);
        log.info("1a.6 conv.delete: userId={} conversationId={} deleted={}",
                userId, conversationId, deleted);
        return ConversationDeleteResponse.builder()
                .deletedMessageCount(deleted)
                .build();
    }

    // ========================================================================
    // helpers
    // ========================================================================

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return null;
        return type.trim();
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    /** 兼容 {@code java.sql.Timestamp}（{@code GROUP BY MIN/MAX} 在 MySQL 端返回）与 {@code LocalDateTime}。 */
    private static LocalDateTime asLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime();
        return LocalDateTime.parse(v.toString().replace(' ', 'T'));
    }

    @SuppressWarnings("unused")
    private static List<Map<String, Object>> emptyRows() {
        return Collections.emptyList();
    }
}
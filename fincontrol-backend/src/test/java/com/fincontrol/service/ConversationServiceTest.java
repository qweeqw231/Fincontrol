package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.conversation.ConversationCreateRequest;
import com.fincontrol.dto.conversation.ConversationDeleteResponse;
import com.fincontrol.dto.conversation.ConversationDetailResponse;
import com.fincontrol.dto.conversation.ConversationListResponse;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.6 Slice C：A6-S05 ~ A6-S07 〔ConversationService 业务测试〕。
 *
 * <p>替身：ChatHistoryMapper mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceTest {

    @Mock
    private ChatHistoryMapper chatHistoryMapper;

    @InjectMocks
    private ConversationService service;

    private static final Long USER_ID = 1L;
    private static final Long USER_ID_2 = 2L;

    private Map<String, Object> rowOf(String convId, String type, String createdAt, String lastAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("conversationId", convId);
        m.put("conversationType", type);
        m.put("createdAt", Timestamp.valueOf(createdAt));
        m.put("lastMessageAt", Timestamp.valueOf(lastAt));
        return m;
    }

    // ========================================================================
    // A6-S05: list — user 隔离 + type 过滤 + 分页
    // ========================================================================

    @Test
    @DisplayName("A6-S05: list 按 user_id 隔离 + type 过滤 + 分页")
    void list_userIsolated_typeFiltered_paginated() {
        when(chatHistoryMapper.countConversations(USER_ID, "ai_assistant")).thenReturn(25);
        when(chatHistoryMapper.selectConversationList(eq(USER_ID), eq("ai_assistant"), eq(10), eq(0)))
                .thenReturn(List.of(
                        rowOf("conv-1", "ai_assistant", "2026-07-15 10:00:00", "2026-07-17 11:00:00"),
                        rowOf("conv-2", "ai_assistant", "2026-07-14 10:00:00", "2026-07-17 10:00:00")
                ));

        ConversationListResponse resp = service.list(USER_ID, "ai_assistant", 1, 10);

        assertThat(resp.getItems()).hasSize(2);
        assertThat(resp.getItems().get(0).getConversationId()).isEqualTo("conv-1");
        assertThat(resp.getItems().get(0).getType()).isEqualTo("ai_assistant");
        assertThat(resp.getItems().get(0).getCreatedAt()).isNotNull();
        assertThat(resp.getItems().get(0).getLastMessageAt()).isNotNull();
        assertThat(resp.getTotal()).isEqualTo(25L);
        assertThat(resp.getPage()).isEqualTo(1);
        assertThat(resp.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("A6-S05: list 不传 type → 不过滤 (null 透传)")
    void list_typeNull_passesNullToMapper() {
        when(chatHistoryMapper.countConversations(USER_ID, null)).thenReturn(5);
        when(chatHistoryMapper.selectConversationList(eq(USER_ID), eq(null), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.list(USER_ID, null, 1, 20);

        verify(chatHistoryMapper).countConversations(USER_ID, null);
        verify(chatHistoryMapper).selectConversationList(USER_ID, null, 20, 0);
    }

    @Test
    @DisplayName("A6-S05: list pageSize > 100 → 截到 100；page < 1 → 1")
    void list_pageAndPageSizeClamp() {
        when(chatHistoryMapper.countConversations(anyLong(), any())).thenReturn(0);
        when(chatHistoryMapper.selectConversationList(anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        // page=0, pageSize=999 → page=1, pageSize=100, offset=0
        service.list(USER_ID, null, 0, 999);
        verify(chatHistoryMapper).selectConversationList(USER_ID, null, 100, 0);

        // page=3, pageSize=10 → offset=20
        service.list(USER_ID, null, 3, 10);
        verify(chatHistoryMapper).selectConversationList(USER_ID, null, 10, 20);
    }

    @Test
    @DisplayName("A6-S05: list type='screenshot_parse' 过滤时不混入 ai_assistant")
    void list_typeFilter_separatesConversations() {
        when(chatHistoryMapper.countConversations(USER_ID, "screenshot_parse")).thenReturn(1);
        when(chatHistoryMapper.selectConversationList(eq(USER_ID), eq("screenshot_parse"), anyInt(), anyInt()))
                .thenReturn(List.of(
                        rowOf("conv-ss1", "screenshot_parse", "2026-07-16 10:00:00", "2026-07-16 10:00:00")
                ));

        ConversationListResponse resp = service.list(USER_ID, "screenshot_parse", 1, 20);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getItems().get(0).getType()).isEqualTo("screenshot_parse");
        // 验证 type 传对了
        verify(chatHistoryMapper).selectConversationList(USER_ID, "screenshot_parse", 20, 0);
    }

    // ========================================================================
    // A6-S06: get 详情
    // ========================================================================

    @Test
    @DisplayName("A6-S06: get 详情 — messages 按 created_at asc，type 从首条推断")
    void get_messagesOrderedByCreatedAt_typeFromFirst() {
        ChatHistory m1 = new ChatHistory();
        m1.setId(1L); m1.setUserId(USER_ID); m1.setConversationId("conv-1");
        m1.setRole("user"); m1.setContent("hello");
        m1.setCreatedAt(LocalDateTime.of(2026, 7, 17, 10, 0));
        m1.setConversationType("ai_assistant");
        ChatHistory m2 = new ChatHistory();
        m2.setId(2L); m2.setUserId(USER_ID); m2.setConversationId("conv-1");
        m2.setRole("assistant"); m2.setContent("hi");
        m2.setCreatedAt(LocalDateTime.of(2026, 7, 17, 10, 1));
        m2.setConversationType("ai_assistant");
        when(chatHistoryMapper.selectByConversationIdOrderByCreatedAt("conv-1"))
                .thenReturn(List.of(m1, m2));

        ConversationDetailResponse resp = service.get(USER_ID, "conv-1");

        assertThat(resp.getConversationId()).isEqualTo("conv-1");
        assertThat(resp.getType()).isEqualTo("ai_assistant");
        assertThat(resp.getMessages()).hasSize(2);
        assertThat(resp.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(resp.getMessages().get(0).getContent()).isEqualTo("hello");
        assertThat(resp.getMessages().get(1).getRole()).isEqualTo("assistant");
    }

    @Test
    @DisplayName("A6-S06: get 不存在 conversation → 抛 2001（SNAPSHOT_NOT_FOUND）")
    void get_notFound_throws2001() {
        when(chatHistoryMapper.selectByConversationIdOrderByCreatedAt("conv-missing"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.get(USER_ID, "conv-missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.SNAPSHOT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("A6-S06: get 其他用户的 conversation → 抛 2001（user 隔离）")
    void get_otherUserConversation_throws2001() {
        ChatHistory m = new ChatHistory();
        m.setId(1L); m.setUserId(USER_ID_2); m.setConversationId("conv-other");
        m.setRole("user"); m.setContent("x");
        m.setCreatedAt(LocalDateTime.now());
        m.setConversationType("ai_assistant");
        when(chatHistoryMapper.selectByConversationIdOrderByCreatedAt("conv-other"))
                .thenReturn(List.of(m));

        assertThatThrownBy(() -> service.get(USER_ID, "conv-other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.SNAPSHOT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("A6-S06: get conversationId 为空 → 抛 2001")
    void get_emptyConversationId_throws2001() {
        assertThatThrownBy(() -> service.get(USER_ID, ""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.SNAPSHOT_NOT_FOUND.getCode());

        assertThatThrownBy(() -> service.get(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.SNAPSHOT_NOT_FOUND.getCode());
    }

    // ========================================================================
    // A6-S07: delete 物理删除
    // ========================================================================

    @Test
    @DisplayName("A6-S07: delete 物理删除 chat_history，返回 deletedMessageCount")
    void delete_returnsDeletedCount() {
        when(chatHistoryMapper.deleteByConversationId("conv-1")).thenReturn(5);

        ConversationDeleteResponse resp = service.delete(USER_ID, "conv-1");

        assertThat(resp.getDeletedMessageCount()).isEqualTo(5);
        verify(chatHistoryMapper).deleteByConversationId("conv-1");
    }

    @Test
    @DisplayName("A6-S07: delete 空对话 → deletedMessageCount=0（不抛错）")
    void delete_emptyConversation_returnsZero() {
        when(chatHistoryMapper.deleteByConversationId("conv-empty")).thenReturn(0);

        ConversationDeleteResponse resp = service.delete(USER_ID, "conv-empty");

        assertThat(resp.getDeletedMessageCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("A6-S07: delete conversationId 为空 → 抛 2001")
    void delete_emptyConversationId_throws2001() {
        assertThatThrownBy(() -> service.delete(USER_ID, ""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.SNAPSHOT_NOT_FOUND.getCode());
    }

    // ========================================================================
    // create（仅返 conversationId，不插库）
    // ========================================================================

    @Test
    @DisplayName("create 合法 type → 返 conversationId (UUID)，不调 mapper.insert")
    void create_validType_returnsUUID_noInsert() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setType("ai_assistant");
        req.setUserId(USER_ID);

        var resp = service.create(USER_ID, req);

        assertThat(resp.getConversationId()).startsWith("conv-");
        assertThat(resp.getConversationId().length()).isGreaterThan(5);
        assertThat(resp.getType()).isEqualTo("ai_assistant");
        assertThat(resp.getCreatedAt()).isNotNull();
        // 关键：[work-plan G4] 决策 — 不插库
        verify(chatHistoryMapper, never()).insert(any(ChatHistory.class));
    }

    @Test
    @DisplayName("create 非法 type → 抛 1001（参数错误）")
    void create_invalidType_throws1001() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setType("invalid_type");
        req.setUserId(USER_ID);

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_SNAPSHOT_DATE.getCode());
    }

    @Test
    @DisplayName("create type 缺省 → 抛 1001")
    void create_missingType_throws1001() {
        ConversationCreateRequest req = new ConversationCreateRequest();
        req.setUserId(USER_ID);

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_SNAPSHOT_DATE.getCode());
    }

    // ========================================================================
    // list — SQL 参数正确性
    // ========================================================================

    @Test
    @DisplayName("list page=2, pageSize=5 → offset=5 传 SQL")
    void list_offsetCalculation() {
        when(chatHistoryMapper.countConversations(USER_ID, null)).thenReturn(12);
        when(chatHistoryMapper.selectConversationList(eq(USER_ID), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        service.list(USER_ID, null, 2, 5);

        ArgumentCaptor<Integer> offsetCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(chatHistoryMapper).selectConversationList(eq(USER_ID), any(), limitCap.capture(), offsetCap.capture());
        assertThat(limitCap.getValue()).isEqualTo(5);
        assertThat(offsetCap.getValue()).isEqualTo(5);
    }

    // ========================================================================
    // list — 空 userId 抛 5001
    // ========================================================================

    @Test
    @DisplayName("list userId 为 null → 抛 5001")
    void list_nullUserId_throws5001() {
        assertThatThrownBy(() -> service.list(null, "ai_assistant", 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }
}
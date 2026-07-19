package com.fincontrol.controller;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.chat.ChatMessageDto;
import com.fincontrol.dto.conversation.ConversationCreateRequest;
import com.fincontrol.dto.conversation.ConversationCreateResponse;
import com.fincontrol.dto.conversation.ConversationDeleteResponse;
import com.fincontrol.dto.conversation.ConversationDetailResponse;
import com.fincontrol.dto.conversation.ConversationListItem;
import com.fincontrol.dto.conversation.ConversationListResponse;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.ChatHistoryMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1a.6 Slice C：A6-C02 ~ A6-C04 〔ConversationController 契约测试〕。
 *
 * <p>只 mock {@link ConversationService}，不加载 Mapper / DB。
 */
@WebMvcTest(
        value = ConversationController.class,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
@TestPropertySource(properties = "spring.profiles.active=local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversationService conversationService;

    @MockBean private FundCategoryMapMapper fundCategoryMapMapper;
    @MockBean private com.fincontrol.mapper.CategoryMasterMapper categoryMasterMapper;
    @MockBean private AssetRawMapper assetRawMapper;
    @MockBean private AssetSnapshotMapper assetSnapshotMapper;
    @MockBean private ChatHistoryMapper chatHistoryMapper;

    // ========================================================================
    // A6-C02: /conversations list
    // ========================================================================

    @Test
    void list_defaultUserId_returnsData() throws Exception {
        ConversationListResponse resp = ConversationListResponse.builder()
                .items(List.of(ConversationListItem.builder()
                        .conversationId("conv-1").type("ai_assistant")
                        .createdAt(LocalDateTime.of(2026, 7, 17, 10, 0))
                        .lastMessageAt(LocalDateTime.of(2026, 7, 17, 11, 0))
                        .build()))
                .total(1L).page(1).pageSize(20)
                .build();
        when(conversationService.list(anyLong(), any(), any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data.items[0].type").value("ai_assistant"))
                .andExpect(jsonPath("$.data.total").value(1));
        verify(conversationService).list(eq(1L), eq(null), eq(null), eq(null));
    }

    @Test
    void list_explicitType_passesThrough() throws Exception {
        when(conversationService.list(anyLong(), anyString(), any(), any()))
                .thenReturn(ConversationListResponse.builder().items(List.of()).total(0L).page(1).pageSize(20).build());

        mockMvc.perform(get("/api/conversations?type=screenshot_parse&page=2&pageSize=10"))
                .andExpect(status().isOk());
        verify(conversationService).list(eq(1L), eq("screenshot_parse"), eq(2), eq(10));
    }

    @Test
    void list_userIdHeader_passesThrough() throws Exception {
        when(conversationService.list(anyLong(), any(), any(), any()))
                .thenReturn(ConversationListResponse.builder().items(List.of()).total(0L).page(1).pageSize(20).build());

        mockMvc.perform(get("/api/conversations").header("X-User-Id", "9"))
                .andExpect(status().isOk());
        verify(conversationService).list(eq(9L), any(), any(), any());
    }

    // ========================================================================
    // A6-C03: /conversations create
    // ========================================================================

    @Test
    void create_validRequest_returnsConversationId() throws Exception {
        ConversationCreateResponse resp = ConversationCreateResponse.builder()
                .conversationId("conv-uuid-123")
                .type("ai_assistant")
                .createdAt(LocalDateTime.of(2026, 7, 17, 10, 0))
                .build();
        when(conversationService.create(anyLong(), any())).thenReturn(resp);

        String body = "{\"type\":\"ai_assistant\"}";
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value("conv-uuid-123"))
                .andExpect(jsonPath("$.data.type").value("ai_assistant"));
        verify(conversationService).create(eq(1L), any(ConversationCreateRequest.class));
    }

    @Test
    void create_invalidType_returns400And1001() throws Exception {
        when(conversationService.create(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "type 必须是 'ai_assistant' 或 'screenshot_parse'"));

        String body = "{\"type\":\"invalid\"}";
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void create_bodyUserIdOverridesHeader() throws Exception {
        ConversationCreateResponse resp = ConversationCreateResponse.builder()
                .conversationId("conv-uuid").type("screenshot_parse")
                .createdAt(LocalDateTime.now()).build();
        when(conversationService.create(anyLong(), any())).thenReturn(resp);

        String body = "{\"type\":\"screenshot_parse\",\"userId\":42}";
        mockMvc.perform(post("/api/conversations")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(conversationService).create(eq(42L), any(ConversationCreateRequest.class));
    }

    // ========================================================================
    // get 详情（A6-C02 之外 bonus 覆盖）
    // ========================================================================

    @Test
    void get_returnsMessagesByCreatedAt() throws Exception {
        ConversationDetailResponse resp = ConversationDetailResponse.builder()
                .conversationId("conv-1")
                .type("ai_assistant")
                .messages(List.of(
                        ChatMessageDto.builder().role("user").content("hello")
                                .createdAt(LocalDateTime.of(2026, 7, 17, 10, 0)).build(),
                        ChatMessageDto.builder().role("assistant").content("hi")
                                .createdAt(LocalDateTime.of(2026, 7, 17, 10, 1)).build()))
                .build();
        when(conversationService.get(anyLong(), anyString())).thenReturn(resp);

        mockMvc.perform(get("/api/conversations/conv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data.type").value("ai_assistant"))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"));
    }

    @Test
    void get_notFound_returns404And2001() throws Exception {
        when(conversationService.get(anyLong(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND, "对话不存在"));

        mockMvc.perform(get("/api/conversations/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2001));
    }

    // ========================================================================
    // A6-C04: /conversations/{id} DELETE
    // ========================================================================

    @Test
    void delete_returnsDeletedMessageCount() throws Exception {
        when(conversationService.delete(anyLong(), anyString()))
                .thenReturn(ConversationDeleteResponse.builder().deletedMessageCount(8).build());

        mockMvc.perform(delete("/api/conversations/conv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deletedMessageCount").value(8));
        verify(conversationService).delete(eq(1L), eq("conv-1"));
    }

    @Test
    void delete_emptyConversation_returnsZero() throws Exception {
        when(conversationService.delete(anyLong(), anyString()))
                .thenReturn(ConversationDeleteResponse.builder().deletedMessageCount(0).build());

        mockMvc.perform(delete("/api/conversations/conv-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedMessageCount").value(0));
    }

    @Test
    void delete_userIdHeader() throws Exception {
        when(conversationService.delete(anyLong(), anyString()))
                .thenReturn(ConversationDeleteResponse.builder().deletedMessageCount(3).build());

        mockMvc.perform(delete("/api/conversations/conv-1").header("X-User-Id", "5"))
                .andExpect(status().isOk());
        verify(conversationService).delete(eq(5L), eq("conv-1"));
    }
}
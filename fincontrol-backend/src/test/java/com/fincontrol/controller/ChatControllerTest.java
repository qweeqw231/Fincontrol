package com.fincontrol.controller;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.chat.ChatMessageDto;
import com.fincontrol.dto.chat.ChatSendRequest;
import com.fincontrol.dto.chat.ChatSendResponse;
import com.fincontrol.dto.chat.IntentClassificationDto;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.ChatHistoryMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1a.6 Slice B：A6-C01 〔ChatController 契约测试〕。
 *
 * <p>只 mock {@link ChatService}，不加载 Mapper / DB；
 * 覆盖 /chat/send 端点的 URL、参数、body、userId 隔离、异常码透传。
 */
@WebMvcTest(
        value = ChatController.class,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
@TestPropertySource(properties = "spring.profiles.active=local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean private FundCategoryMapMapper fundCategoryMapMapper;
    @MockBean private AssetRawMapper assetRawMapper;
    @MockBean private AssetSnapshotMapper assetSnapshotMapper;
    @MockBean private ChatHistoryMapper chatHistoryMapper;

    private ChatSendResponse fixtureResp(boolean isInvestment, String routedTo) {
        return ChatSendResponse.builder()
                .conversationId("conv-test-uuid")
                .userMessage(ChatMessageDto.builder()
                        .role("user").content("test").createdAt(LocalDateTime.now()).build())
                .assistantMessage(ChatMessageDto.builder()
                        .role("assistant").content("ai response")
                        .createdAt(LocalDateTime.now())
                        .routedTo(routedTo)
                        .promptVersion(isInvestment ? "ai_assistant v1.0" : null)
                        .build())
                .intentClassification(IntentClassificationDto.builder()
                        .result(isInvestment).latencyMs(123).build())
                .build();
    }

    // ========================================================================
    // A6-C01: /chat/send URL/body/异常码
    // ========================================================================

    @Test
    void send_defaultUserIdInvestment_returnsMainLoop() throws Exception {
        when(chatService.send(anyLong(), any())).thenReturn(fixtureResp(true, "main_loop"));

        String body = "{\"message\":\"本月应该补仓多少\"}";
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value("conv-test-uuid"))
                .andExpect(jsonPath("$.data.assistantMessage.routedTo").value("main_loop"))
                .andExpect(jsonPath("$.data.assistantMessage.promptVersion").value("ai_assistant v1.0"))
                .andExpect(jsonPath("$.data.intentClassification.result").value(true))
                .andExpect(jsonPath("$.data.intentClassification.latencyMs").value(123));
        verify(chatService).send(eq(1L), any(ChatSendRequest.class));
    }

    @Test
    void send_defaultUserIdNonInvestment_returnsGarbageLoop() throws Exception {
        when(chatService.send(anyLong(), any())).thenReturn(fixtureResp(false, "garbage_loop"));

        String body = "{\"message\":\"今天天气怎么样\"}";
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.assistantMessage.routedTo").value("garbage_loop"))
                .andExpect(jsonPath("$.data.assistantMessage.promptVersion").doesNotExist())
                .andExpect(jsonPath("$.data.intentClassification.result").value(false));
    }

    @Test
    void send_explicitHeaderUserId_passesThrough() throws Exception {
        when(chatService.send(anyLong(), any())).thenReturn(fixtureResp(false, "garbage_loop"));

        String body = "{\"message\":\"test\"}";
        mockMvc.perform(post("/api/chat/send")
                        .header("X-User-Id", "9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(chatService).send(eq(9L), any(ChatSendRequest.class));
    }

    @Test
    void send_bodyUserIdOverridesHeader() throws Exception {
        when(chatService.send(anyLong(), any())).thenReturn(fixtureResp(false, "garbage_loop"));

        // header 是 1，但 body 指定 userId=42
        String body = "{\"message\":\"test\",\"userId\":42}";
        mockMvc.perform(post("/api/chat/send")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(chatService).send(eq(42L), any(ChatSendRequest.class));
    }

    @Test
    void send_emptyMessage_returns400And1001() throws Exception {
        when(chatService.send(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "message 必填且非空"));

        String body = "{\"message\":\"\"}";
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void send_aiError_returns5xxAndCorrectCode() throws Exception {
        when(chatService.send(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.VISION_INVALID_JSON, "上游非 JSON"));

        String body = "{\"message\":\"test\"}";
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway())  // 3001 -> 502
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    void send_conversationIdPassedThrough() throws Exception {
        when(chatService.send(anyLong(), any())).thenReturn(
                ChatSendResponse.builder()
                        .conversationId("conv-existing")
                        .userMessage(ChatMessageDto.builder().role("user").content("x").build())
                        .assistantMessage(ChatMessageDto.builder().role("assistant").content("y")
                                .routedTo("main_loop").promptVersion("ai_assistant v1.0").build())
                        .intentClassification(IntentClassificationDto.builder().result(true).latencyMs(0).build())
                        .build());

        String body = "{\"conversationId\":\"conv-existing\",\"message\":\"test\"}";
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("conv-existing"));
    }
}
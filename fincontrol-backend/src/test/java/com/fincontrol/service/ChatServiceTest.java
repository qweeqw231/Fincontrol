package com.fincontrol.service;

import com.fincontrol.ai.AiRouter;
import com.fincontrol.ai.IntentClassifier;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.chat.ChatSendRequest;
import com.fincontrol.dto.chat.ChatSendResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.6 Slice B：A6-S01 ~ A6-S04 〔ChatService 业务测试〕。1a.8 改为 mock {@link AiRouter} 统一入口。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock private ChatHistoryMapper chatHistoryMapper;
    @Mock private IntentClassifier intentClassifier;
    @Mock private AiRouter aiRouter; // 1a.8 替换原 TextAiClient
    @Mock private PromptLoaderService promptLoader;

    @InjectMocks private ChatService service;

    private static final Long USER_ID = 1L;
    private static final String AI_PROMPT = "你是 FinControl AI 顾问...";

    private void stubChatSuccess(String content) {
        when(aiRouter.callChat(any(), anyString()))
                .thenReturn(AiRouter.ChatResult.success(ChatHistory.PROVIDER_MINIMAX, false, content));
    }

    @Test
    @DisplayName("A6-S01: 投资决策类 → main_loop，promptVersion='ai_assistant v1.0'")
    void send_investmentClass_routesToMainLoop() {
        when(intentClassifier.isInvestmentRelated("本月应该补仓多少")).thenReturn(true);
        when(promptLoader.get("ai_assistant")).thenReturn(AI_PROMPT);
        stubChatSuccess("基于您的规则，建议...");
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("本月应该补仓多少");
        req.setUserId(USER_ID);

        ChatSendResponse resp = service.send(USER_ID, req);

        assertThat(resp.getConversationId()).startsWith("conv-");
        assertThat(resp.getAssistantMessage().getRoutedTo()).isEqualTo("main_loop");
        assertThat(resp.getAssistantMessage().getPromptVersion()).isEqualTo("ai_assistant v1.0");
        assertThat(resp.getAssistantMessage().getContent()).isEqualTo("基于您的规则，建议...");
        assertThat(resp.getIntentClassification().isResult()).isTrue();
        assertThat(resp.getIntentClassification().getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(resp.getUserMessage().getRole()).isEqualTo("user");
        assertThat(resp.getUserMessage().getContent()).isEqualTo("本月应该补仓多少");

        verify(aiRouter).callChat(eq(AI_PROMPT), eq("本月应该补仓多少"));
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    @DisplayName("A6-S02: 非投资决策类 → garbage_loop，systemPrompt=null，无 promptVersion")
    void send_nonInvestmentClass_routesToGarbageLoop() {
        when(intentClassifier.isInvestmentRelated("今天天气怎么样")).thenReturn(false);
        when(aiRouter.callChat(isNull(), anyString()))
                .thenReturn(AiRouter.ChatResult.success(ChatHistory.PROVIDER_MINIMAX, false, "我是闲聊助手..."));
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("今天天气怎么样");
        req.setUserId(USER_ID);

        ChatSendResponse resp = service.send(USER_ID, req);

        assertThat(resp.getAssistantMessage().getRoutedTo()).isEqualTo("garbage_loop");
        assertThat(resp.getAssistantMessage().getPromptVersion()).isNull();
        assertThat(resp.getAssistantMessage().getContent()).isEqualTo("我是闲聊助手...");
        assertThat(resp.getIntentClassification().isResult()).isFalse();

        verify(aiRouter).callChat(isNull(), eq("今天天气怎么样"));
        verify(promptLoader, never()).get("ai_assistant");
    }

    @Test
    @DisplayName("A6-S03: conversationId 空 → 自动生成 UUID 前缀 conv-")
    void send_emptyConversationId_autoGenerate() {
        when(intentClassifier.isInvestmentRelated(anyString())).thenReturn(true);
        when(promptLoader.get("ai_assistant")).thenReturn(AI_PROMPT);
        stubChatSuccess("ok");
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("test");
        req.setUserId(USER_ID);

        ChatSendResponse resp = service.send(USER_ID, req);

        assertThat(resp.getConversationId()).startsWith("conv-");
        assertThat(resp.getConversationId().length()).isGreaterThan(5);
    }

    @Test
    @DisplayName("A6-S03: conversationId 已存在 → 复用，不生成新 ID")
    void send_existingConversationId_kept() {
        when(intentClassifier.isInvestmentRelated(anyString())).thenReturn(false);
        when(aiRouter.callChat(isNull(), anyString()))
                .thenReturn(AiRouter.ChatResult.success(ChatHistory.PROVIDER_MINIMAX, false, "ok"));
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("test");
        req.setUserId(USER_ID);
        req.setConversationId("conv-existing-uuid");

        ChatSendResponse resp = service.send(USER_ID, req);

        assertThat(resp.getConversationId()).isEqualTo("conv-existing-uuid");
    }

    @Test
    @DisplayName("A6-S04: 写 chat_history — user 消息先于 assistant，conversationType='ai_assistant'")
    void send_writesBothMessagesToChatHistory() {
        when(intentClassifier.isInvestmentRelated(anyString())).thenReturn(true);
        when(promptLoader.get("ai_assistant")).thenReturn(AI_PROMPT);
        stubChatSuccess("ai response");
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("hello");
        req.setUserId(USER_ID);

        service.send(USER_ID, req);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryMapper, times(2)).insert(captor.capture());

        ChatHistory user = captor.getAllValues().get(0);
        assertThat(user.getRole()).isEqualTo("user");
        assertThat(user.getContent()).isEqualTo("hello");
        assertThat(user.getUserId()).isEqualTo(USER_ID);
        assertThat(user.getConversationType()).isEqualTo("ai_assistant");
        assertThat(user.getConversationId()).startsWith("conv-");
        assertThat(user.getCreatedAt()).isNotNull();

        ChatHistory assistant = captor.getAllValues().get(1);
        assertThat(assistant.getRole()).isEqualTo("assistant");
        assertThat(assistant.getContent()).isEqualTo("ai response");
        assertThat(assistant.getUserId()).isEqualTo(USER_ID);
        assertThat(assistant.getConversationType()).isEqualTo("ai_assistant");
        assertThat(assistant.getConversationId()).isEqualTo(user.getConversationId());
        assertThat(assistant.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("userId 为 null → 抛 5001（INTERNAL_ERROR）")
    void send_nullUserId_throws5001() {
        assertThatThrownBy(() -> service.send(null, new ChatSendRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("请求体为 null → 抛 1001（参数错误）")
    void send_nullRequest_throws1001() {
        assertThatThrownBy(() -> service.send(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_SNAPSHOT_DATE.getCode());
    }

    @Test
    @DisplayName("message 为空 → 抛 1001")
    void send_emptyMessage_throws1001() {
        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("   ");
        assertThatThrownBy(() -> service.send(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_SNAPSHOT_DATE.getCode());
        verify(chatHistoryMapper, never()).insert(any(ChatHistory.class));
    }

    @Test
    @DisplayName("AiRouter 抛 3001 → 透传 + 写 assistant 错误记录")
    void send_aiRouterError_propagates() {
        when(intentClassifier.isInvestmentRelated(anyString())).thenReturn(true);
        when(promptLoader.get("ai_assistant")).thenReturn(AI_PROMPT);
        when(aiRouter.callChat(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.VISION_INVALID_JSON, "上游非 JSON"));
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("test");
        req.setUserId(USER_ID);

        assertThatThrownBy(() -> service.send(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());

        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    @DisplayName("IntentClassifier 抛 3001 → fallback 响应 + 写 assistant 错误记录（不抛给用户，不调 AiRouter）")
    void send_intentClassifierError_fallbackResponse() {
        when(intentClassifier.isInvestmentRelated(anyString()))
                .thenThrow(new BusinessException(ErrorCode.VISION_INVALID_JSON, "intent 失败"));
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("test");
        req.setUserId(USER_ID);

        ChatSendResponse resp = service.send(USER_ID, req);

        assertThat(resp.getAssistantMessage().getRoutedTo()).isEqualTo("garbage_loop");
        assertThat(resp.getAssistantMessage().getContent()).contains("error code=3001");
        assertThat(resp.getIntentClassification().isResult()).isFalse();
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
        verify(aiRouter, never()).callChat(any(), any());
        verify(promptLoader, never()).get("ai_assistant");
    }

    @Test
    @DisplayName("latencyMs ≥ 0（与 System.currentTimeMillis 差不为负）")
    void send_latencyNonNegative() {
        when(intentClassifier.isInvestmentRelated(anyString())).thenReturn(true);
        when(promptLoader.get("ai_assistant")).thenReturn(AI_PROMPT);
        stubChatSuccess("ok");
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatSendRequest req = new ChatSendRequest();
        req.setMessage("test");
        req.setUserId(USER_ID);

        ChatSendResponse resp = service.send(USER_ID, req);

        assertThat(resp.getIntentClassification().getLatencyMs()).isGreaterThanOrEqualTo(0);
    }
}

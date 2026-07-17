package com.fincontrol.service;

import com.fincontrol.ai.IntentClassifier;
import com.fincontrol.ai.TextAiClient;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.chat.ChatMessageDto;
import com.fincontrol.dto.chat.ChatSendRequest;
import com.fincontrol.dto.chat.ChatSendResponse;
import com.fincontrol.dto.chat.IntentClassificationDto;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 1a.18 /api/chat/send 业务编排（[api-contract.md §8.1](#)，[P0-3.5] [P0-3.6]）。
 *
 * <p>流程：
 * <ol>
 *   <li>校验 message 非空（空 → 1001）</li>
 *   <li>若 {@code conversationId} 为空，生成 UUID（新对话）</li>
 *   <li>写 user 消息到 {@code chat_history}（{@code conversation_type='ai_assistant'}）</li>
 *   <li>调 {@link IntentClassifier#isInvestmentRelated}（记 latency）</li>
 *   <li>路由：
 *       <ul>
 *         <li>投资类 → 加载 {@code ai_assistant} prompt → 调 {@link TextAiClient#chat}（main_loop）</li>
 *         <li>非投资类 → systemPrompt=null → 调 {@link TextAiClient#chat}（garbage_loop）</li>
 *       </ul>
 *   </li>
 *   <li>写 assistant 消息（成功）或 assistant 错误记录（失败）</li>
 *   <li>返回 {@link ChatSendResponse}</li>
 * </ol>
 *
 * <p>错误码：[api-contract.md §11](#)。
 *
 * <p>非事务（参考 1a.2 {@code ScreenshotService.parse} 的设计）：user 消息先落库，
 * 失败时仅写 assistant 错误记录；避免 3001/3002 抛错时把已写 user 消息回滚。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public static final String PROMPTVersion = "ai_assistant v1.0";
    public static final String ROUTED_MAIN = "main_loop";
    public static final String ROUTED_GARBAGE = "garbage_loop";

    private final ChatHistoryMapper chatHistoryMapper;
    private final IntentClassifier intentClassifier;
    private final TextAiClient textAiClient;
    private final PromptLoaderService promptLoader;

    public ChatService(ChatHistoryMapper chatHistoryMapper,
                       IntentClassifier intentClassifier,
                       TextAiClient textAiClient,
                       PromptLoaderService promptLoader) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.intentClassifier = intentClassifier;
        this.textAiClient = textAiClient;
        this.promptLoader = promptLoader;
    }

    /**
     * 1a.18 主入口。
     *
     * @param userId 用户 ID（来自 X-User-Id header 或 body）
     * @param req    请求体
     * @return 响应（conversationId + userMessage + assistantMessage + intentClassification）
     */
    public ChatSendResponse send(Long userId, ChatSendRequest req) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "请求体为空");
        }
        String message = req.getMessage();
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "message 必填且非空");
        }

        // 1) conversationId
        String conversationId = (req.getConversationId() == null || req.getConversationId().isBlank())
                ? "conv-" + UUID.randomUUID()
                : req.getConversationId();
        log.info("1a.6 chat.send: userId={} conversationId={} message.len={}",
                userId, conversationId, message.length());

        // 2) 写 user 消息
        LocalDateTime userCreatedAt = LocalDateTime.now();
        ChatHistory userMsg = new ChatHistory();
        userMsg.setUserId(userId);
        userMsg.setConversationId(conversationId);
        userMsg.setRole(ChatHistory.ROLE_USER);
        userMsg.setContent(message);
        userMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_AI_ASSISTANT);
        userMsg.setCreatedAt(userCreatedAt);
        chatHistoryMapper.insert(userMsg);

        // 3) 意图分类
        long startMs = System.currentTimeMillis();
        boolean isInvestment;
        try {
            isInvestment = intentClassifier.isInvestmentRelated(message);
        } catch (BusinessException e) {
            // AI 调用失败：不抛给用户（破坏对话），写 assistant 错误记录后用垃圾回路兜底
            log.warn("1a.6 intent classifier failed, fallback to garbage_loop: code={} msg={}",
                    e.getErrorCode().getCode(), e.getMessage());
            persistAssistantError(conversationId, userId, e);
            // 构造最小响应（fallback）
            return ChatSendResponse.builder()
                    .conversationId(conversationId)
                    .userMessage(toDto(userMsg))
                    .assistantMessage(ChatMessageDto.builder()
                            .role(ChatHistory.ROLE_ASSISTANT)
                            .content("[error code=" + e.getErrorCode().getCode() + "] " + e.getMessage())
                            .createdAt(LocalDateTime.now())
                            .routedTo(ROUTED_GARBAGE)
                            .build())
                    .intentClassification(IntentClassificationDto.builder()
                            .result(false)
                            .latencyMs((int) (System.currentTimeMillis() - startMs))
                            .build())
                    .build();
        }
        long latencyMs = System.currentTimeMillis() - startMs;

        // 4) 路由 + 调 TextAiClient
        String systemPrompt;
        String routedTo;
        String promptVersion;
        if (isInvestment) {
            systemPrompt = promptLoader.get("ai_assistant");
            routedTo = ROUTED_MAIN;
            promptVersion = PROMPTVersion;
        } else {
            systemPrompt = null;
            routedTo = ROUTED_GARBAGE;
            promptVersion = null;
        }

        String aiContent;
        try {
            aiContent = textAiClient.chat(systemPrompt, message);
        } catch (BusinessException e) {
            // AI 调用失败：写 assistant 错误记录，抛给用户
            persistAssistantError(conversationId, userId, e);
            throw e;
        }

        // 5) 写 assistant 消息
        LocalDateTime assistantCreatedAt = LocalDateTime.now();
        ChatHistory assistantMsg = new ChatHistory();
        assistantMsg.setUserId(userId);
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole(ChatHistory.ROLE_ASSISTANT);
        assistantMsg.setContent(aiContent);
        assistantMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_AI_ASSISTANT);
        assistantMsg.setCreatedAt(assistantCreatedAt);
        chatHistoryMapper.insert(assistantMsg);

        // 6) 构造响应
        return ChatSendResponse.builder()
                .conversationId(conversationId)
                .userMessage(toDto(userMsg))
                .assistantMessage(ChatMessageDto.builder()
                        .role(ChatHistory.ROLE_ASSISTANT)
                        .content(aiContent)
                        .createdAt(assistantCreatedAt)
                        .routedTo(routedTo)
                        .promptVersion(promptVersion)
                        .build())
                .intentClassification(IntentClassificationDto.builder()
                        .result(isInvestment)
                        .latencyMs((int) latencyMs)
                        .build())
                .build();
    }

    private ChatMessageDto toDto(ChatHistory msg) {
        return ChatMessageDto.builder()
                .role(msg.getRole())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    /**
     * 写 assistant 错误记录（参考 1a.2 ScreenshotService.persistAssistantError 模式）。
     * <br>不影响主流程：失败仅 log warn。
     */
    private void persistAssistantError(String conversationId, Long userId, BusinessException e) {
        ChatHistory errMsg = new ChatHistory();
        errMsg.setUserId(userId);
        errMsg.setConversationId(conversationId);
        errMsg.setRole(ChatHistory.ROLE_ASSISTANT);
        errMsg.setContent("[error code=" + e.getErrorCode().getCode() + "] " + e.getMessage());
        errMsg.setConversationType(ChatHistory.CONVERSATION_TYPE_AI_ASSISTANT);
        errMsg.setCreatedAt(LocalDateTime.now());
        try {
            chatHistoryMapper.insert(errMsg);
        } catch (Exception ex) {
            log.warn("写入失败 assistant 记录失败（不阻塞主流程）: {}", ex.getMessage());
        }
    }
}
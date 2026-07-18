package com.fincontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.ai.AiProperties;
import com.fincontrol.ai.ApiStyle;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 多模态视觉模型客户端（Phase 1a.5+，1a.8 重构）。
 *
 * <p>1a.8 关键改动：
 * <ul>
 *   <li>从 {@code @Value} 字段注入改为 {@link AiProperties} 构造器注入（fincontrol.ai.vision.minimax.*）</li>
 *   <li>加 {@link ApiStyle} 枚举（OPENAI_CHAT / OPENAI_RESPONSES），callRaw 重载支持指定</li>
 *   <li>保留旧 {@code callRaw(file, sys, msg)} 签名（默认 OPENAI_CHAT）— 不破坏现有 ScreenshotService</li>
 *   <li>OPENAI_CHAT 路径实现 = 1a.5 minimax OpenAI compat 形态（messages[] + image_url）</li>
 *   <li>OPENAI_RESPONSES 路径占位（Commit C 实现豆包 ARK）</li>
 * </ul>
 *
 * <p>失败映射：[api-contract.md §11](#) 错误码 3001/3002/3003 通用：
 * <ul>
 *   <li>上游返回非 JSON → BusinessException(3001)</li>
 *   <li>调用超时       → BusinessException(3002)</li>
 *   <li>0 只基金       → BusinessException(3003)（由 Service 层判定）</li>
 * </ul>
 *
 * <p>构造器策略：
 * <ul>
 *   <li>{@link #VisionModelClient(AiProperties)} — Spring 自动发现（{@code @Autowired} 标注），注入 minimax 配置</li>
 *   <li>{@link #VisionModelClient(OkHttpClient, ObjectMapper)} — 1a.7 单测兼容（默认 Provider 占位）</li>
 *   <li>{@link #VisionModelClient(OkHttpClient, ObjectMapper, String, String, String, int)} — 详细 6 字段单测构造</li>
 * </ul>
 */
@Service
public class VisionModelClient {

    private static final Logger log = LoggerFactory.getLogger(VisionModelClient.class);

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    /**
     * 1a.8 主构造器（Spring 自动发现）。
     * <p>从 {@link AiProperties#getVision()}{@code .getMinimax()} 注入。
     */
    @Autowired
    public VisionModelClient(AiProperties properties) {
        this(defaultHttp(), new ObjectMapper(),
                properties.getVision().getMinimax());
    }

    /**
     * 单参构造器 — 从 Provider 配置注入（单测/详细构造友好）。
     */
    public VisionModelClient(OkHttpClient http, ObjectMapper objectMapper, AiProperties.Provider minimax) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.apiKey = minimax.getApiKey();
        this.baseUrl = minimax.getBaseUrl();
        this.model = minimax.getModel();
        this.timeoutSeconds = minimax.getTimeoutSeconds();
    }

    /**
     * 1a.7 老单测兼容构造器（保持 VisionModelClientTest 如果存在的不破坏）。
     * <p>默认 Provider 占位（API key 为 REPLACE_ME，调用时抛 5001 友好提示）。
     */
    public VisionModelClient(OkHttpClient http, ObjectMapper objectMapper) {
        this(http, objectMapper, defaultMinimaxProvider());
    }

    /**
     * 详细构造器 — 单测可独立注入 6 字段。
     */
    public VisionModelClient(OkHttpClient http, ObjectMapper objectMapper,
                             String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 旧 API 入口（默认 OPENAI_CHAT，保持 ScreenshotService 不破坏）。
     */
    public String callRaw(File imageFile, String systemPrompt, String userMessage) {
        return callRaw(imageFile, systemPrompt, userMessage, ApiStyle.OPENAI_CHAT);
    }

    /**
     * 1a.8 新增：按 ApiStyle 路由到对应 provider schema。
     */
    public String callRaw(File imageFile, String systemPrompt, String userMessage, ApiStyle apiStyle) {
        Objects.requireNonNull(apiStyle, "apiStyle");
        switch (apiStyle) {
            case OPENAI_CHAT:
                return callOpenAiChat(imageFile, systemPrompt, userMessage);
            case OPENAI_RESPONSES:
                return callOpenAiResponses(imageFile, systemPrompt, userMessage);
            default:
                throw new IllegalArgumentException("Unsupported ApiStyle: " + apiStyle);
        }
    }

    /**
     * 提取 JSON（不变，迁移到 VisionModelClient 本类）。
     */
    public JsonNode extractFirstJsonObject(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON, "视觉模型响应为空");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignore) { /* fall through */ }
        int start = raw.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            for (int i = start; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String candidate = raw.substring(start, i + 1);
                        try {
                            return objectMapper.readTree(candidate);
                        } catch (Exception ignore) {
                            break;
                        }
                    }
                }
            }
            start = raw.indexOf('{', start + 1);
        }
        String truncated = raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
        throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                "视觉模型响应无 JSON 对象: " + truncated);
    }

    // ===================================================================
    // 内部：OPENAI_CHAT（minimax 现有 OpenAI 兼容形态）
    // ===================================================================
    private String callOpenAiChat(File imageFile, String systemPrompt, String userMessage) {
        // 1. 校验
        validateConfig();
        if (imageFile == null || !imageFile.isFile()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "图片文件不存在");
        }

        // 2. 读图 → base64 dataURL
        String dataUrl;
        try {
            byte[] bytes = Files.readAllBytes(imageFile.toPath());
            String mime = Files.probeContentType(imageFile.toPath());
            if (mime == null) mime = "image/png";
            dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取图片失败: " + e.getMessage());
        }

        // 3. 构造 OpenAI-兼容 messages + body
        String body;
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            String userText = (userMessage != null && !userMessage.isBlank()) ? userMessage : "请解析以下截图";
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", userText));
            parts.add(Map.of("type", "image_url",
                    "image_url", Map.of("url", dataUrl)));
            messages.add(Map.of("role", "user", "content", parts));

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", model);
            req.put("messages", messages);
            req.put("stream", false);
            body = objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构造请求体失败: " + e.getMessage());
        }

        // 4. 发请求（minimax OpenAI compat）
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        return executeRequest(request, "minimax OpenAI chat completions");
    }

    // ===================================================================
    // 内部：OPENAI_RESPONSES（豆包 ARK，1a.8 Commit C 实现）
    // ===================================================================
    private String callOpenAiResponses(File imageFile, String systemPrompt, String userMessage) {
        // Commit C 实施位置 — 当前抛 501 NOT_IMPLEMENTED 友好提示
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "OPENAI_RESPONSES（豆包 ARK）尚未实现，请等待 1a.8 Commit C 完成后重新调用。");
    }

    // ===================================================================
    // 内部：HTTP 执行（共享 try/catch + 错误码映射）
    // ===================================================================
    private String executeRequest(Request request, String providerLabel) {
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.warn("{} 非 2xx: status={} body={}", providerLabel, response.code(), errBody);
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        providerLabel + " HTTP " + response.code() + ": " + errBody);
            }
            String respBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(respBody);
            // 1a.8：开放给两个 schema 共用 — 优先抽 content 字段，没有则抽 output[0].content[0].text（豆包）
            String content = extractContentFromProvider(root);
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        providerLabel + " 响应 content 为空: " + respBody);
            }
            return content;
        } catch (SocketTimeoutException ste) {
            throw new BusinessException(ErrorCode.VISION_TIMEOUT,
                    providerLabel + " 调用超时 (>" + timeoutSeconds + "s): " + ste.getMessage());
        } catch (JsonProcessingException jpe) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    providerLabel + " 响应解析失败: " + jpe.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    providerLabel + " 网络错误: " + e.getMessage());
        }
    }

    /**
     * 抽取 provider 响应中的 content 文本。
     * <p>OpenAI Chat 形态：{@code choices[0].message.content}
     * <p>OpenAI Responses 形态：{@code output[0].content[0].text}（豆包 ARK）
     */
    private String extractContentFromProvider(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText("");
        }
        JsonNode output = root.path("output");
        if (output.isArray() && output.size() > 0) {
            JsonNode content = output.get(0).path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText("");
            }
        }
        return "";
    }

    // ===================================================================
    // 内部：配置校验（提取为方法，便于单元测试复用）
    // ===================================================================
    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank()
                || "REPLACE_ME_VISION_API_KEY".equals(apiKey)
                || "REPLACE_ME".equals(apiKey)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "视觉模型 API Key 未配置（请设置 fincontrol.ai.vision.minimax.api-key 或 application-local.yml）");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "fincontrol.ai.vision.minimax.base-url 未配置");
        }
        if (model == null || model.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "fincontrol.ai.vision.minimax.model 未配置");
        }
    }

    // ===================================================================
    // 静态工具：默认 OkHttp + 默认 Provider（1a.7 测试兼容）
    // ===================================================================
    private static OkHttpClient defaultHttp() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    private static AiProperties.Provider defaultMinimaxProvider() {
        AiProperties.Provider p = new AiProperties.Provider();
        p.setApiKey("REPLACE_ME_VISION_API_KEY");
        p.setBaseUrl("https://api.minimaxi.com/v1");
        p.setModel("MiniMax-M3");
        p.setTimeoutSeconds(300);
        return p;
    }
}

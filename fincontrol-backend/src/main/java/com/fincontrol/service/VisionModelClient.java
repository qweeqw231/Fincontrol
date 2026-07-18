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
 * 多模态视觉模型客户端（Phase 1a.5+，1a.8 双 provider 完整实现）。
 *
 * <p>1a.8 完整改动：
 * <ul>
 *   <li>从 {@code @Value} 字段注入改为构造器注入，{@link AiProperties} 注入 minimax + 豆包 双 Provider</li>
 *   <li>加 {@link ApiStyle} 枚举（OPENAI_CHAT / OPENAI_RESPONSES）</li>
 *   <li>保留旧 {@code callRaw(file, sys, msg)} 签名（默认 OPENAI_CHAT）— 不破坏 ScreenshotService</li>
 *   <li>OPENAI_CHAT：minimax OpenAI compat（/v1/chat/completions）— 1a.5+ 沿用</li>
 *   <li>OPENAI_RESPONSES：豆包 ARK（/api/v3/responses，input[] + input_image）— 1a.8 Commit C 实现</li>
 *   <li>两个 provider 各自独立 OkHttpClient（连接池隔离）</li>
 *   <li>共用 {@code extractContentFromProvider} 同时支持两种 schema 响应抽取</li>
 * </ul>
 *
 * <p>失败映射（[api-contract.md §11](#)）：3001/3002/3003 沿用，provider label 加在错误信息中便于排查。
 */
@Service
public class VisionModelClient {

    private static final Logger log = LoggerFactory.getLogger(VisionModelClient.class);

    // minimax primary（OpenAI /chat/completions）
    private final String minimaxApiKey;
    private final String minimaxBaseUrl;
    private final String minimaxModel;
    private final OkHttpClient minimaxHttp;

    // 豆包 ARK fallback（OpenAI /responses）
    private final String doubaoApiKey;
    private final String doubaoBaseUrl;
    private final String doubaoModel;
    private final OkHttpClient doubaoHttp;

    private final ObjectMapper objectMapper;

    /**
     * 1a.8 主构造器（Spring 自动发现）。
     * <p>从 {@link AiProperties#getVision()} 注入 minimax + doubao 双 Provider。
     */
    @Autowired
    public VisionModelClient(AiProperties properties) {
        this(defaultMinimaxHttp(), defaultDoubaoHttp(), new ObjectMapper(),
                properties.getVision().getMinimax(),
                properties.getVision().getDoubao());
    }

    /**
     * 详细单测构造器（单测可独立注入两 provider + 两 OkHttpClient）。
     */
    public VisionModelClient(OkHttpClient minimaxHttp, OkHttpClient doubaoHttp,
                             ObjectMapper objectMapper,
                             AiProperties.Provider minimax,
                             AiProperties.Provider doubao) {
        this.minimaxHttp = minimaxHttp;
        this.doubaoHttp = doubaoHttp;
        this.objectMapper = objectMapper;
        this.minimaxApiKey = minimax.getApiKey();
        this.minimaxBaseUrl = minimax.getBaseUrl();
        this.minimaxModel = minimax.getModel();
        this.doubaoApiKey = doubao.getApiKey();
        this.doubaoBaseUrl = doubao.getBaseUrl();
        this.doubaoModel = doubao.getModel();
    }

    /**
     * 1a.7 老单测兼容构造器（保持现有 VisionModelClientTest 等不破坏）。
     */
    public VisionModelClient(OkHttpClient http, ObjectMapper objectMapper) {
        this(http, http, objectMapper, defaultMinimaxProvider(), defaultDoubaoProvider());
    }

    /**
     * 详细 6 字段构造器 — 旧 1a.7 单测使用 minimax 字段占位；doubao 占位。
     */
    public VisionModelClient(OkHttpClient http, ObjectMapper objectMapper,
                             String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this(http, http, objectMapper,
                buildMinimaxProvider(apiKey, baseUrl, model, timeoutSeconds),
                buildDoubaoProvider("REPLACE_ME_DOUBAO_VISION_API_KEY", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-8-251228", 300));
    }

    // ===================================================================
    // 公共入口
    // ===================================================================

    /** 旧 API（默认 OPENAI_CHAT，保持 ScreenshotService 不破坏）。 */
    public String callRaw(File imageFile, String systemPrompt, String userMessage) {
        return callRaw(imageFile, systemPrompt, userMessage, ApiStyle.OPENAI_CHAT);
    }

    /** 1a.8 新增：按 ApiStyle 路由到对应 provider schema。 */
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

    /** 提取 JSON（与 1a.7 兼容，未改）。 */
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
    // 内部：OPENAI_CHAT（minimax OpenAI compat）
    // ===================================================================
    private String callOpenAiChat(File imageFile, String systemPrompt, String userMessage) {
        validateProviderConfig("minimax", minimaxApiKey, minimaxBaseUrl, minimaxModel);
        if (imageFile == null || !imageFile.isFile()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "图片文件不存在");
        }

        // 1. 读图 → base64 dataURL
        String dataUrl = readImageAsDataUrl(imageFile);

        // 2. 构造 OpenAI-兼容 body（messages[] + image_url）
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
            req.put("model", minimaxModel);
            req.put("messages", messages);
            req.put("stream", false);
            body = objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构造请求体失败: " + e.getMessage());
        }

        Request request = new Request.Builder()
                .url(minimaxBaseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + minimaxApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        return executeRequest(minimaxHttp, request, "minimax OpenAI chat completions");
    }

    // ===================================================================
    // 内部：OPENAI_RESPONSES（豆包 ARK，1a.8 Commit C 实现）
    // ===================================================================
    /**
     * 豆包 ARK /api/v3/responses 实现（1a.8 Commit C）。
     * <p>请求体（OpenAI Responses 形态）：
     * <pre>
     * {
     *   "model": "doubao-seed-1-8-251228",
     *   "input": [
     *     {
     *       "role": "user",
     *       "content": [
     *         { "type": "input_text", "text": "请解析以下截图" },
     *         { "type": "input_image", "image_url": "data:image/png;base64,..." }
     *       ]
     *     }
     *   ],
     *   "stream": false
     * }
     * </pre>
     * <p>响应体（抽 {@code output[0].content[0].text}）：
     * <pre>
     * {
     *   "output": [
     *     { "content": [ { "type": "output_text", "text": "..." } ] }
     *   ]
     * }
     * </pre>
     */
    private String callOpenAiResponses(File imageFile, String systemPrompt, String userMessage) {
        validateProviderConfig("doubao", doubaoApiKey, doubaoBaseUrl, doubaoModel);
        if (imageFile == null || !imageFile.isFile()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "图片文件不存在");
        }

        // 1. 读图 → base64 dataURL
        String dataUrl = readImageAsDataUrl(imageFile);

        // 2. 构造 OpenAI Responses 形态 body（input[] + input_image）
        String body;
        try {
            String userText = (userMessage != null && !userMessage.isBlank()) ? userMessage : "请解析以下截图";

            // input[] 单元素，role=user，content=[input_text, input_image]
            Map<String, Object> userInput = new LinkedHashMap<>();
            userInput.put("role", "user");
            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of("type", "input_text", "text", userText));
            contentParts.add(Map.of("type", "input_image", "image_url", dataUrl));
            userInput.put("content", contentParts);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", doubaoModel);
            req.put("input", List.of(userInput));
            req.put("stream", false);

            // 1a.8：豆包 system prompt 通过 instructions 字段传递（如 ARK 支持）
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                req.put("instructions", systemPrompt);
            }

            body = objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构造请求体失败: " + e.getMessage());
        }

        Request request = new Request.Builder()
                .url(doubaoBaseUrl + "/responses")
                .addHeader("Authorization", "Bearer " + doubaoApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        return executeRequest(doubaoHttp, request, "doubao ARK responses");
    }

    // ===================================================================
    // 内部工具：复用
    // ===================================================================
    private String readImageAsDataUrl(File imageFile) {
        try {
            byte[] bytes = Files.readAllBytes(imageFile.toPath());
            String mime = Files.probeContentType(imageFile.toPath());
            if (mime == null) mime = "image/png";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取图片失败: " + e.getMessage());
        }
    }

    private String executeRequest(OkHttpClient http, Request request, String providerLabel) {
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.warn("{} 非 2xx: status={} body={}", providerLabel, response.code(), errBody);
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        providerLabel + " HTTP " + response.code() + ": " + errBody);
            }
            String respBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(respBody);
            String content = extractContentFromProvider(root);
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        providerLabel + " 响应 content 为空: " + respBody);
            }
            return content;
        } catch (SocketTimeoutException ste) {
            throw new BusinessException(ErrorCode.VISION_TIMEOUT,
                    providerLabel + " 调用超时: " + ste.getMessage());
        } catch (JsonProcessingException jpe) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    providerLabel + " 响应解析失败: " + jpe.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    providerLabel + " 网络错误: " + e.getMessage());
        }
    }

    /** 抽 content — OpenAI Chat / OpenAI Responses 两种 schema 共用 */
    private String extractContentFromProvider(JsonNode root) {
        // OpenAI Chat: choices[0].message.content
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText("");
        }
        // OpenAI Responses: output[0].content[0].text
        JsonNode output = root.path("output");
        if (output.isArray() && output.size() > 0) {
            JsonNode content = output.get(0).path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText("");
            }
        }
        return "";
    }

    private void validateProviderConfig(String label, String apiKey, String baseUrl, String model) {
        if (apiKey == null || apiKey.isBlank()
                || apiKey.startsWith("REPLACE_ME")
                || "REPLACE_ME".equals(apiKey)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "视觉模型 " + label + " API Key 未配置（请设置 fincontrol.ai.vision." + label + ".api-key）");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "视觉模型 " + label + " base-url 未配置");
        }
        if (model == null || model.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "视觉模型 " + label + " model 未配置");
        }
    }

    // ===================================================================
    // 静态工具：默认 OkHttp + 默认 Provider（1a.7 测试 + 占位启动兼容）
    // ===================================================================
    private static OkHttpClient defaultMinimaxHttp() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    private static OkHttpClient defaultDoubaoHttp() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    private static AiProperties.Provider defaultMinimaxProvider() {
        return buildMinimaxProvider("REPLACE_ME_VISION_API_KEY", "https://api.minimaxi.com/v1", "MiniMax-M3", 300);
    }

    private static AiProperties.Provider defaultDoubaoProvider() {
        return buildDoubaoProvider("REPLACE_ME_DOUBAO_VISION_API_KEY", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-8-251228", 300);
    }

    private static AiProperties.Provider buildMinimaxProvider(String key, String url, String model, int timeout) {
        AiProperties.Provider p = new AiProperties.Provider();
        p.setApiKey(key);
        p.setBaseUrl(url);
        p.setModel(model);
        p.setTimeoutSeconds(timeout);
        return p;
    }

    private static AiProperties.Provider buildDoubaoProvider(String key, String url, String model, int timeout) {
        AiProperties.Provider p = new AiProperties.Provider();
        p.setApiKey(key);
        p.setBaseUrl(url);
        p.setModel(model);
        p.setTimeoutSeconds(timeout);
        return p;
    }
}

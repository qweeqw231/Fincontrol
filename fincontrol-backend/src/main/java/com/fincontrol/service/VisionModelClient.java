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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
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
        this(buildHttp(properties.getVision().getMinimax().getTimeoutSeconds()),
                buildHttp(properties.getVision().getDoubao().getTimeoutSeconds()),
                new ObjectMapper(),
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

    /** 旧单图 API（默认 OPENAI_CHAT），委托给 1a.10 多图实现。 */
    public String callRaw(File imageFile, String systemPrompt, String userMessage) {
        return callRaw(java.util.Collections.singletonList(imageFile),
                systemPrompt, userMessage, ApiStyle.OPENAI_CHAT);
    }

    /** 旧单图 + ApiStyle API，保持现有调用方兼容。 */
    public String callRaw(File imageFile, String systemPrompt, String userMessage, ApiStyle apiStyle) {
        return callRaw(java.util.Collections.singletonList(imageFile), systemPrompt, userMessage, apiStyle);
    }

    /** 1a.10：一次请求传入有序多图；顺序用于跨页截断补全。 */
    public String callRaw(List<File> imageFiles, String systemPrompt,
                          String userMessage, ApiStyle apiStyle) {
        Objects.requireNonNull(apiStyle, "apiStyle");
        List<File> validated = validateImageFiles(imageFiles);
        switch (apiStyle) {
            case OPENAI_CHAT:
                return callOpenAiChat(validated, systemPrompt, userMessage);
            case OPENAI_RESPONSES:
                return callOpenAiResponses(validated, systemPrompt, userMessage);
            default:
                throw new IllegalArgumentException("Unsupported ApiStyle: " + apiStyle);
        }
    }

    /**
     * 从不受控模型响应中选择完整的资产 JSON 根对象。
     *
     * <p>MiniMax 可能返回 {@code <think>}、Markdown、schema 示例、局部 fund JSON，最后才给出
     * 完整 {@code categories[].funds[]}。旧实现返回“第一个可解析对象”，会误选局部 fund，
     * 导致后续得到 {@code categories=[]}。1a.10 改为：
     *
     * <ol>
     *   <li>若整个响应就是 JSON，保持原有直返行为；</li>
     *   <li>否则以字符串/转义感知的方式提取所有平衡花括号候选；</li>
     *   <li>按资产根 schema 和完整基金数量评分，得分相同时选择后出现的候选。</li>
     * </ol>
     */
    public JsonNode extractFirstJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON, "视觉模型响应为空");
        }

        try {
            JsonNode direct = objectMapper.readTree(raw);
            if (direct != null && direct.isObject()) {
                return direct;
            }
        } catch (Exception ignore) {
            // 响应含 think/Markdown 时进入候选扫描。
        }

        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String candidate : extractBalancedJsonObjects(raw)) {
            try {
                JsonNode parsed = objectMapper.readTree(candidate);
                if (parsed == null || !parsed.isObject()) continue;
                int score = assetRootScore(parsed);
                // >=：相同 schema/基金数时优先最终输出，而不是前面的示例。
                if (score >= bestScore) {
                    best = parsed;
                    bestScore = score;
                }
            } catch (Exception ignore) {
                // 单个候选无效不影响后续候选。
            }
        }
        if (best != null) {
            log.debug("从混合模型响应中选择 JSON 根对象：score={} fields={}",
                    bestScore, best.size());
            return best;
        }

        String truncated = raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
        throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                "视觉模型响应无 JSON 对象: " + truncated);
    }

    /** 提取所有平衡的 JSON object；忽略字符串值内部及转义后的花括号。 */
    private static List<String> extractBalancedJsonObjects(String raw) {
        List<String> candidates = new ArrayList<>();
        Deque<Integer> starts = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                starts.push(i);
            } else if (c == '}' && !starts.isEmpty()) {
                int start = starts.pop();
                candidates.add(raw.substring(start, i + 1));
            }
        }
        return candidates;
    }

    /** 完整资产根远高于局部 fund；完整基金越多得分越高。 */
    private static int assetRootScore(JsonNode node) {
        int score = 0;
        JsonNode categories = node.path("categories");
        if (categories.isArray()) {
            score += 10_000 + categories.size() * 100;
            for (JsonNode category : categories) {
                if (category.hasNonNull("category_name")) score += 10;
                JsonNode funds = category.path("funds");
                if (funds.isArray()) score += funds.size() * 1_000;
            }
        }
        JsonNode holdings = node.path("holdings");
        if (holdings.isArray()) {
            score += 9_000 + holdings.size() * 1_000;
        }
        if (node.has("snapshot_date") || node.has("date")) score += 100;
        if (node.has("total_asset")) score += 100;
        if (node.has("matchedFunds") || node.has("matched_funds")) score += 20;
        if (node.has("fund_name") || node.has("name")) score += 1;
        if (node.has("amount")) score += 1;
        return score;
    }

    // ===================================================================
    // 内部：OPENAI_CHAT（minimax OpenAI compat）
    // ===================================================================
    private String callOpenAiChat(List<File> imageFiles, String systemPrompt, String userMessage) {
        validateProviderConfig("minimax", minimaxApiKey, minimaxBaseUrl, minimaxModel);

        // 构造 OpenAI-兼容 body（messages[] + 有序 image_url parts）
        String body;
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            String userText = (userMessage != null && !userMessage.isBlank()) ? userMessage : "请解析以下截图";
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", userText));
            for (File imageFile : imageFiles) {
                parts.add(Map.of("type", "image_url",
                        "image_url", Map.of("url", readImageAsDataUrl(imageFile))));
            }
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
    private String callOpenAiResponses(List<File> imageFiles, String systemPrompt, String userMessage) {
        validateProviderConfig("doubao", doubaoApiKey, doubaoBaseUrl, doubaoModel);

        // 构造 OpenAI Responses 形态 body（input[] + 有序 input_image parts）
        String body;
        try {
            String userText = (userMessage != null && !userMessage.isBlank()) ? userMessage : "请解析以下截图";

            // input[] 单元素，role=user，content=[input_text, input_image]
            Map<String, Object> userInput = new LinkedHashMap<>();
            userInput.put("role", "user");
            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of("type", "input_text", "text", userText));
            for (File imageFile : imageFiles) {
                contentParts.add(Map.of("type", "input_image",
                        "image_url", readImageAsDataUrl(imageFile)));
            }
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
    private static List<File> validateImageFiles(List<File> imageFiles) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "图片列表至少包含 1 张图");
        }
        List<File> copy = List.copyOf(imageFiles);
        for (File imageFile : copy) {
            if (imageFile == null || !imageFile.isFile()) {
                throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE,
                        "图片文件不存在: " + (imageFile == null ? "null" : imageFile.getPath()));
            }
        }
        return copy;
    }

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
        long startMs = System.currentTimeMillis();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                long latencyMs = System.currentTimeMillis() - startMs;
                log.warn("{} 非 2xx: status={} body={}", providerLabel, response.code(), errBody);
                writeProviderFailureAudit(providerLabel, "HTTP_" + response.code(),
                        response.code(), errBody, latencyMs);
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        providerLabel + " HTTP " + response.code() + ": " + errBody);
            }
            String respBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(respBody);
            String content = extractContentFromProvider(root);
            if (content.isBlank()) {
                long latencyMs = System.currentTimeMillis() - startMs;
                writeProviderFailureAudit(providerLabel, "EMPTY_CONTENT", 200,
                        "响应 content 为空", latencyMs);
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        providerLabel + " 响应 content 为空: " + respBody);
            }
            return content;
        } catch (SocketTimeoutException ste) {
            long latencyMs = System.currentTimeMillis() - startMs;
            writeProviderFailureAudit(providerLabel, "TIMEOUT", 0, ste.getMessage(), latencyMs);
            throw new BusinessException(ErrorCode.VISION_TIMEOUT,
                    providerLabel + " 调用超时: " + ste.getMessage());
        } catch (JsonProcessingException jpe) {
            long latencyMs = System.currentTimeMillis() - startMs;
            writeProviderFailureAudit(providerLabel, "JSON_PARSE_ERROR", 0, jpe.getMessage(), latencyMs);
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    providerLabel + " 响应解析失败: " + jpe.getMessage());
        } catch (IOException e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            writeProviderFailureAudit(providerLabel, "NETWORK_ERROR", 0, e.getMessage(), latencyMs);
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    providerLabel + " 网络错误: " + e.getMessage());
        }
    }

    /**
     * 1a.10 P1（§9.3 修复）：provider 失败时落盘结构化审计日志。
     * <p>路径 {@code docs/test-records/ocr-results/{date}/}（与 OCR 成功日志同目录，gitignored），
     * 文件名 {@code {provider}-failure-{ts}.json}，含 provider / errorClass / statusCode / errorMessage / latencyMs / timestamp。
     * <p>这样豆包 primary 失败后 5 秒内能定位原因（401 鉴权？429 限流？408 实际超时？网络断？）。
     */
    private void writeProviderFailureAudit(String provider, String errorClass,
                                            int statusCode, String errorMessage, long latencyMs) {
        try {
            String date = java.time.LocalDate.now().toString();
            java.nio.file.Path dir = java.nio.file.Paths.get("docs", "test-records",
                    "ocr-results", date);
            java.nio.file.Files.createDirectories(dir);
            String ts = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            java.util.Map<String, Object> audit = new java.util.LinkedHashMap<>();
            audit.put("event", "provider_failure");
            audit.put("provider", provider);
            audit.put("errorClass", errorClass);
            audit.put("statusCode", statusCode);
            audit.put("errorMessage", errorMessage == null ? "" : errorMessage);
            audit.put("latencyMs", latencyMs);
            audit.put("timestamp", java.time.LocalDateTime.now().toString());
            java.nio.file.Path file = dir.resolve(provider + "-failure-" + ts + ".json");
            java.nio.file.Files.writeString(file, objectMapper.writeValueAsString(audit));
            log.warn("provider failure audit written: {}", file);
        } catch (Exception auditEx) {
            log.error("provider failure audit 落盘失败（非致命）: {}", auditEx.getMessage());
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

    /**
     * 1a.10 P1（§9.2 修复）：OkHttp client 读 {@code timeoutSeconds}（来自 {@code AiProperties.Provider}）。
     * 之前两个 static 方法写死 60/120s，导致配置 {@code fincontrol.ai.vision.{minimax|doubao}.timeout-seconds=300} 不生效。
     * 现在 read/write/connect 三个超时都用 {@code timeoutSeconds}，与 application.yml 配置一致。
     */
    private static OkHttpClient buildHttp(int timeoutSeconds) {
        int t = Math.max(1, timeoutSeconds);
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(t, TimeUnit.SECONDS)
                .writeTimeout(t, TimeUnit.SECONDS)
                .build();
    }

    /** 1a.7 老单测兼容：60s readTimeout 写死（仅供 VisionModelClientTest 旧版构造器）。 */
    private static OkHttpClient defaultMinimaxHttp() {
        return buildHttp(60);
    }

    /** 1a.7 老单测兼容：120s readTimeout 写死（仅供 VisionModelClientTest 旧版构造器）。 */
    private static OkHttpClient defaultDoubaoHttp() {
        return buildHttp(120);
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

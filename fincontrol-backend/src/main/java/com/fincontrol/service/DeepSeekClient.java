package com.fincontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek API 客户端（OpenAI-兼容 /chat/completions）。
 *
 * <p>输入：图片文件路径 + system prompt（来自 prompt_versions.screenshot_parser v1.0）。
 * 输出：解析后的 JSON（与 [api-contract.md §2.2 响应 data](#) 兼容）。
 *
 * <p>失败映射：[api-contract.md §11](#)：
 * <ul>
 *   <li>DeepSeek 返回非 JSON  → BusinessException(3001)</li>
 *   <li>调用超时              → BusinessException(3002)</li>
 *   <li>解析后 0 只基金        → BusinessException(3003)（由 Service 层判定）</li>
 * </ul>
 */
@Service
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${fincontrol.deepseek.base-url}")
    private String baseUrl = "https://api.deepseek.com/v1";

    @Value("${fincontrol.deepseek.model}")
    private String model = "deepseek-flash";

    @Value("${fincontrol.deepseek.timeout-seconds:30}")
    private int timeoutSeconds = 30;

    public DeepSeekClient() {
        this(new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build(),
                new ObjectMapper());
    }

    /** 单元测试可通过此构造器注入 OkHttp / ObjectMapper / 配置。 */
    public DeepSeekClient(OkHttpClient http, ObjectMapper objectMapper) {
        this.http = http;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 DeepSeek 解析图片，返回 assistant 原始响应（Markdown + 嵌入 JSON）。
     *
     * @param imageFile    截图文件（jpg/png/webp）
     * @param systemPrompt screenshot_parser v1.0 prompt 全文
     * @param userMessage  user 侧描述（可空，默认 "请解析以下截图"）
     * @return DeepSeek 模型 assistant 消息的原始文本
     * @throws BusinessException 超时 → 3002；网络/响应异常 → 3001
     */
    public String callRaw(File imageFile, String systemPrompt, String userMessage) {
        if (imageFile == null || !imageFile.isFile()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "图片文件不存在");
        }
        if (apiKey == null || apiKey.isBlank() || "REPLACE_ME_DEEPSEEK_API_KEY".equals(apiKey)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "DeepSeek API Key 未配置（请设置 DEEPSEEK_API_KEY 或 application-local.yml）");
        }

        // 1. 读图 → base64 dataURL
        String dataUrl;
        try {
            byte[] bytes = Files.readAllBytes(imageFile.toPath());
            String mime = Files.probeContentType(imageFile.toPath());
            if (mime == null) mime = "image/png";
            dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取图片失败: " + e.getMessage());
        }

        // 2. 构造 OpenAI 兼容 messages + body
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

        // 3. 发请求
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.warn("DeepSeek 非 2xx: status={} body={}", response.code(), errBody);
                throw new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON,
                        "DeepSeek API HTTP " + response.code() + ": " + errBody);
            }
            String respBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON,
                        "DeepSeek 响应无 choices: " + respBody);
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON,
                        "DeepSeek 响应 content 为空: " + respBody);
            }
            return content;
        } catch (SocketTimeoutException ste) {
            throw new BusinessException(ErrorCode.DEEPSEEK_TIMEOUT,
                    "DeepSeek API 调用超时 (>" + timeoutSeconds + "s): " + ste.getMessage());
        } catch (JsonProcessingException jpe) {
            throw new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON,
                    "DeepSeek 上游响应解析失败: " + jpe.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON,
                    "DeepSeek API 网络错误: " + e.getMessage());
        }
    }

    /**
     * 从 DeepSeek 原始响应中抽取首个 JSON 对象（模型同时输出 Markdown 表格 + JSON）。
     * 1. 整段先按 JSON 解析；失败则扫描首层 { ... } 区间。
     */
    public JsonNode extractFirstJsonObject(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON, "DeepSeek 响应为空");
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
        throw new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON,
                "DeepSeek 响应无 JSON 对象: " + truncated);
    }
}

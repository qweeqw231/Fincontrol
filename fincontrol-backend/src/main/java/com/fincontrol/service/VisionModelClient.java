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
 * 多模态视觉模型客户端（Phase 1a.5+）。
 *
 * <p>替代旧版 DeepSeekClient（仅文本），由 MiniMax M3 系列原生多模态替代（图片 + 视频输入）。
 * 实现按 OpenAI-compatible chat completions schema：基地址/模型名按 application.yml 配置，
 * 消息体 message[{role,content:["text",{type:image_url,image_url:{url:data:image/...;base64,...}}]}]。
 *
 * <p>失败映射：[api-contract.md §11](.. / phase-0/api-contract.md) 错误码 3001/3002 通用：
 * <ul>
 *   <li>上游返回非 JSON → BusinessException(3001)
 *   <li>调用超时       → BusinessException(3002)
 *   <li>0 只基金       → BusinessException(3003)（由 Service 层判定）
 * </ul>
 *
 * <p>如果 minimax 实际接口 schema 与 OpenAI-compat 不同，本类在内部切换请求体拼接即可；
 * 其它 12 个文件（Controller / DTO / Service / ErrorCode / 等）不受影响。
 */
@Service
public class VisionModelClient {

    private static final Logger log = LoggerFactory.getLogger(VisionModelClient.class);

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;

    @Value("${fincontrol.vision.api-key}")
    private String apiKey;

    @Value("${fincontrol.vision.base-url}")
    private String baseUrl = "https://api.minimaxi.chat/v1";

    @Value("${fincontrol.vision.model}")
    private String model = "MiniMax-Text-01";

    @Value("${fincontrol.vision.timeout-seconds:60}")
    private int timeoutSeconds = 60;

    public VisionModelClient() {
        this(new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build(),
                new ObjectMapper());
    }

    /** 单元测试可通过此构造器注入 OkHttp / ObjectMapper。 */
    public VisionModelClient(OkHttpClient http, ObjectMapper objectMapper) {
        this.http = http;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用视觉模型解析图片，返回 assistant 原始响应（Markdown + 嵌入 JSON）。
     */
    public String callRaw(File imageFile, String systemPrompt, String userMessage) {
        if (imageFile == null || !imageFile.isFile()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "图片文件不存在");
        }
        if (apiKey == null || apiKey.isBlank() || "REPLACE_ME_VISION_API_KEY".equals(apiKey)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "视觉模型 API Key 未配置（请设置 fincontrol.vision.api-key 或 application-local.yml）");
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

        // 2. 构造 OpenAI-兼容 messages + body
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
                log.warn("视觉模型非 2xx: status={} body={}", response.code(), errBody);
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        "视觉模型 HTTP " + response.code() + ": " + errBody);
            }
            String respBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        "视觉模型响应无 choices: " + respBody);
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        "视觉模型响应 content 为空: " + respBody);
            }
            return content;
        } catch (SocketTimeoutException ste) {
            throw new BusinessException(ErrorCode.VISION_TIMEOUT,
                    "视觉模型调用超时 (>" + timeoutSeconds + "s): " + ste.getMessage());
        } catch (JsonProcessingException jpe) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    "视觉模型响应解析失败: " + jpe.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    "视觉模型网络错误: " + e.getMessage());
        }
    }

    /**
     * 从视觉模型原始响应中抽取首个 JSON 对象（模型同时输出 Markdown 表格 + JSON）。
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
}

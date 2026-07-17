package com.fincontrol.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文本 AI 客户端（[ai-client-split.md §3.2](#) 〔v1.0〕）。
 *
 * <p>与 {@code VisionModelClient} 完全隔离：
 * <ul>
 *   <li>独立 {@code @ConfigurationProperties}（{@code fincontrol.ai.text.*}）</li>
 *   <li>独立 OkHttpClient 实例（避免连接池与 vision 端互相阻塞）</li>
 *   <li>独立 temperature / maxTokens / responseFormat</li>
 *   <li>无 system prompt 时调垃圾回路（{@code null} system）</li>
 * </ul>
 *
 * <p>实现按 OpenAI-compatible chat completions schema：基地址/模型名按 {@code AiProperties.Text} 配置，
 * 消息体 {@code messages:[{role,content}]}，无 image_url 多模态部分。
 *
 * <p>失败映射：[api-contract.md §11](#) 错误码 3001/3002 通用：
 * <ul>
 *   <li>上游返回非 JSON → BusinessException(3001)</li>
 *   <li>调用超时 → BusinessException(3002)</li>
 * </ul>
 *
 * <p>如果 minimax text-only 实际接口 schema 与 OpenAI-compat 不同，本类在内部切换请求体拼接即可；
 * 其它文件（Controller / DTO / Service / ErrorCode 等）不受影响。
 */
public class TextAiClient {

    private static final Logger log = LoggerFactory.getLogger(TextAiClient.class);

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final AiProperties.Text config;

    /** 生产用构造函数（由 AiConfig 注册）。 */
    public TextAiClient(AiProperties.Text config) {
        this(config,
                new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(120, TimeUnit.SECONDS)
                        .build(),
                new ObjectMapper());
    }

    /** 单元测试可通过此构造器注入 OkHttp / ObjectMapper。 */
    public TextAiClient(AiProperties.Text config, OkHttpClient http, ObjectMapper objectMapper) {
        this.config = config;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    public AiProperties.Text getConfig() {
        return config;
    }

    /**
     * 调用文本模型，返回 assistant 响应 content。
     *
     * @param systemPrompt 系统提示词（main_loop 传 {@code ai_assistant} prompt；garbage_loop 传 {@code null}）
     * @param userMessage  用户消息（明文）
     * @return assistant content
     * @throws BusinessException 3001 非 JSON / 4xx-5xx / 3002 超时
     */
    public String chat(String systemPrompt, String userMessage) {
        validateConfig();
        if (userMessage == null || userMessage.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "userMessage 必填且非空");
        }

        // 1) 构造 messages
        String body;
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", config.getModel());
            req.put("messages", messages);
            req.put("temperature", config.getTemperature());
            req.put("max_tokens", config.getMaxTokens());
            req.put("stream", false);
            body = objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "构造请求体失败: " + e.getMessage());
        }

        // 2) 发送请求
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.warn("文本模型非 2xx: status={} body={}", response.code(), errBody);
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        "文本模型 HTTP " + response.code() + ": " + errBody);
            }
            ResponseBody respBody = response.body();
            if (respBody == null) {
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON, "文本模型响应 body 为空");
            }
            String bodyStr = respBody.string();
            JsonNode root = objectMapper.readTree(bodyStr);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        "文本模型响应无 choices: " + bodyStr);
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        "文本模型响应 content 为空: " + bodyStr);
            }
            return content;
        } catch (SocketTimeoutException ste) {
            throw new BusinessException(ErrorCode.VISION_TIMEOUT,
                    "文本模型调用超时 (>" + config.getTimeoutSeconds() + "s): " + ste.getMessage());
        } catch (JsonProcessingException jpe) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    "文本模型响应解析失败: " + jpe.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VISION_INVALID_JSON,
                    "文本模型网络错误: " + e.getMessage());
        }
    }

    private void validateConfig() {
        if (config == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TextAiClient config 未注入");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()
                || "REPLACE_ME_TEXT_AI_KEY".equals(config.getApiKey())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "文本模型 API Key 未配置（请设置 fincontrol.ai.text.api-key 或 application-local.yml）");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "fincontrol.ai.text.base-url 未配置");
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "fincontrol.ai.text.model 未配置");
        }
    }
}
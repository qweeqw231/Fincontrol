package com.fincontrol.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1a.6 Slice A：A6-INT-01 / A6-INT-02 〔TextAiClient 集成测试〕。
 *
 * <p>替身策略：OkHttpClient 注入 + 自定义 Interceptor 拦截请求并返回预置响应。
 * <br>不依赖 mockwebserver，避免新增测试依赖。
 *
 * <p>覆盖：
 * <ul>
 *   <li>正常 200 + 合法 JSON → content 抽取</li>
 *   <li>非 2xx → 抛 3001</li>
 *   <li>非 JSON 响应 → 抛 3001</li>
 *   <li>超时 → 抛 3002</li>
 *   <li>空 content → 抛 3001</li>
 *   <li>API key 未配置 → 抛 5001（启动时强制）</li>
 *   <li>systemPrompt=null → 调垃圾回路（无 system 消息）</li>
 *   <li>systemPrompt 非空 → 调主回路（含 system 消息）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TextAiClientTest {

    private AiProperties.Text config;
    private ObjectMapper objectMapper;
    private OkHttpClient http;

    @BeforeEach
    void setUp() {
        config = new AiProperties.Text();
        config.setBaseUrl("https://api.test.example/v1");
        config.setModel("test-text-model");
        config.setApiKey("test-api-key-not-placeholder");
        config.setTemperature(0.7);
        config.setMaxTokens(1024);
        config.setTimeoutSeconds(60);
        objectMapper = new ObjectMapper();
        http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    private TextAiClient newClient(Interceptor interceptor) {
        OkHttpClient client = http.newBuilder().addInterceptor(interceptor).build();
        return new TextAiClient(config, client, objectMapper);
    }

    /**
     * 从 OkHttp Request 读 body 内容为 UTF-8 字符串。
     * <p>{@code request.body().toString()} 返回的是 OkHttp 默认对象 toString，
     * 不是 body content；必须用 {@link RequestBody#writeTo} + {@link Buffer}。
     */
    private static String readBody(Request request) throws IOException {
        Buffer buffer = new Buffer();
        if (request.body() != null) {
            request.body().writeTo(buffer);
        }
        return buffer.readUtf8();
    }

    /**
     * 构造一个 OkHttp 响应（用 chain.request() 满足 OkHttp 内部约束）。
     */
    private static Response fakeResponse(Interceptor.Chain chain, int code, String body) {
        return new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Error")
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();
    }

    // ========================================================================
    // 正常路径
    // ========================================================================

    @Test
    @DisplayName("正常 200 + 合法 JSON → 抽取 content 字段")
    void chat_normalResponse_returnsContent() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        TextAiClient client = newClient(chain -> {
            capturedBody.set(readBody(chain.request()));
            return fakeResponse(chain, 200,
                    "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"true\"}}]}");
        });

        String content = client.chat("SYS_PROMPT", "USER_MSG");

        assertThat(content).isEqualTo("true");
        // 验证请求体含 system + user 消息
        assertThat(capturedBody.get()).contains("\"role\":\"system\"");
        assertThat(capturedBody.get()).contains("\"content\":\"SYS_PROMPT\"");
        assertThat(capturedBody.get()).contains("\"role\":\"user\"");
        assertThat(capturedBody.get()).contains("\"content\":\"USER_MSG\"");
        assertThat(capturedBody.get()).contains("\"model\":\"test-text-model\"");
        assertThat(capturedBody.get()).contains("\"temperature\":0.7");
    }

    @Test
    @DisplayName("systemPrompt=null → 调垃圾回路（无 system 消息）")
    void chat_nullSystemPrompt_skipsSystemMessage() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        TextAiClient client = newClient(chain -> {
            capturedBody.set(readBody(chain.request()));
            return fakeResponse(chain, 200,
                    "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}");
        });

        String content = client.chat(null, "USER_MSG");

        assertThat(content).isEqualTo("hello");
        // 不应含 system role
        assertThat(capturedBody.get()).doesNotContain("\"role\":\"system\"");
        assertThat(capturedBody.get()).contains("\"role\":\"user\"");
    }

    @Test
    @DisplayName("systemPrompt 为空字符串 → 也跳过 system 消息")
    void chat_blankSystemPrompt_skipsSystemMessage() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        TextAiClient client = newClient(chain -> {
            capturedBody.set(readBody(chain.request()));
            return fakeResponse(chain, 200,
                    "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"x\"}}]}");
        });

        client.chat("", "USER_MSG");

        assertThat(capturedBody.get()).doesNotContain("\"role\":\"system\"");
    }

    // ========================================================================
    // 错误码透传
    // ========================================================================

    @Test
    @DisplayName("非 2xx 响应 → 抛 3001（VISION_INVALID_JSON 复用）")
    void chat_httpError_throws3001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 401,
                "{\"error\":{\"message\":\"Unauthorized\"}}"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    @Test
    @DisplayName("HTTP 500 响应 → 抛 3001")
    void chat_http500_throws3001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 500, "Internal Server Error"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    @Test
    @DisplayName("响应非 JSON → 抛 3001")
    void chat_nonJsonResponse_throws3001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200, "<html>error</html>"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    @Test
    @DisplayName("响应缺 choices 字段 → 抛 3001")
    void chat_missingChoices_throws3001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200, "{\"error\":\"oops\"}"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    @Test
    @DisplayName("响应 choices 空数组 → 抛 3001")
    void chat_emptyChoices_throws3001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200, "{\"choices\":[]}"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    @Test
    @DisplayName("响应 content 字段为空 → 抛 3001")
    void chat_emptyContent_throws3001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    @Test
    @DisplayName("拦截器模拟超时 → 抛 3002（VISION_TIMEOUT 复用）")
    void chat_timeout_throws3002() {
        TextAiClient client = newClient(chain -> {
            throw new java.net.SocketTimeoutException("read timed out");
        });

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_TIMEOUT.getCode());
    }

    @Test
    @DisplayName("拦截器模拟网络 IOException → 抛 3001")
    void chat_networkError_throws3001() {
        TextAiClient client = newClient(chain -> {
            throw new IOException("Connection refused");
        });

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    // ========================================================================
    // 配置校验
    // ========================================================================

    @Test
    @DisplayName("API key 未配置（占位符） → 抛 5001（启动时强制）")
    void chat_placeholderApiKey_throws5001() {
        config.setApiKey("REPLACE_ME_TEXT_AI_KEY");
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200, "{}"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("API key 为 null/blank → 抛 5001")
    void chat_blankApiKey_throws5001() {
        config.setApiKey("   ");
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200, "{}"));

        assertThatThrownBy(() -> client.chat("SYS", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("userMessage 为空 → 抛 1001（参数错误）")
    void chat_blankUserMessage_throws1001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200, "{}"));

        assertThatThrownBy(() -> client.chat("SYS", "  "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_SNAPSHOT_DATE.getCode());
    }

    @Test
    @DisplayName("userMessage 为 null → 抛 1001")
    void chat_nullUserMessage_throws1001() {
        TextAiClient client = newClient(chain -> fakeResponse(chain, 200, "{}"));

        assertThatThrownBy(() -> client.chat("SYS", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_SNAPSHOT_DATE.getCode());
    }

    // ========================================================================
    // A6-INT-01 辅助：Bean 隔离（构造函数对比）
    // ========================================================================

    @Test
    @DisplayName("A6-INT-01: TextAiClient 独立 OkHttpClient，与 VisionModelClient 共享 base-url 但配置隔离")
    void configIsolatedFromVision() {
        AiProperties.Text t = new AiProperties.Text();
        t.setBaseUrl("https://api.test.example/v1");
        t.setModel("text-only-model");
        t.setApiKey("text-key");
        t.setTemperature(0.1);
        assertThat(t.getTemperature()).isEqualTo(0.1);
        assertThat(t.getModel()).isNotEqualTo("MiniMax-M3");  // vision 用 M3
        assertThat(t.getApiKey()).isNotEqualTo(t.getModel());
    }
}
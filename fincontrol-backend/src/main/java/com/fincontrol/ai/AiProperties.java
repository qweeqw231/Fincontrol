package com.fincontrol.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 客户端配置（[ai-client-split.md](#)，1a.8 升级）。
 *
 * <p>1a.8 起统一为 1 个根配置 {@code fincontrol.ai.*}，包含：
 * <ul>
 *   <li>{@code fincontrol.ai.vision} — 视觉模型（minimax primary + 豆包 OPENAI_RESPONSES fallback）</li>
 *   <li>{@code fincontrol.ai.text} — 文本模型（minimax M3 primary + DeepSeek fallback）</li>
 *   <li>{@code fincontrol.ai.router} — 路由策略（imageCountThreshold + cache + retry/CB 配置）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>视觉与文本 Bean 各自独立的配置前缀（{@code fincontrol.ai.vision.*} vs {@code fincontrol.ai.text.*}）</li>
 *   <li>视觉与文本 Bean 各自独立的 OkHttpClient 实例（连接池不互相阻塞）</li>
 *   <li>每个 provider 都有 primary 与 fallback 双段；字段缺失时填占位符（启动不抛，但首次调用抛 5001/5002）</li>
 *   <li>路由策略独立 {@code fincontrol.ai.router.*} 段，可按场景调节 imageCount 阈值与 cache TTL</li>
 * </ul>
 *
 * <p>1a.10 扩展：单 {@code Provider} 加 {@code apiStyle} 字段，让豆包能选 OPENAI_CHAT
 * （走 ARK /api/v3/chat/completions）而不是默认 OPENAI_RESPONSES。
 */
@ConfigurationProperties(prefix = "fincontrol.ai")
public class AiProperties {

    /** 1a.8：Vision 配置段（minimax + 豆包 双 Provider） */
    private Vision vision = new Vision();

    /** 1a.7 已存在（flat 字段），1a.8 加 Fallback inner class */
    private Text text = new Text();

    /** 1a.8：路由策略段 */
    private Router router = new Router();

    public Vision getVision() { return vision; }
    public void setVision(Vision vision) { this.vision = vision; }

    public Text getText() { return text; }
    public void setText(Text text) { this.text = text; }

    public Router getRouter() { return router; }
    public void setRouter(Router router) { this.router = router; }

    // ==========================================================
    // Vision（1a.8 重构）：minimax M3 (primary) + 豆包 OPENAI_RESPONSES (fallback)
    // ==========================================================
    /**
     * 视觉模型配置（1a.8 重构）。
     * <p>对应 {@code fincontrol.ai.vision.*}，包含 minimax primary 与豆包 fallback 双 provider。
     */
    public static class Vision {
        /** minimax vision primary（OpenAI 兼容 chat completions）。 */
        private Provider minimax = new Provider();
        /** 豆包 vision fallback（OpenAI Responses API）。 */
        private Provider doubao = new Provider();

        public Provider getMinimax() { return minimax; }
        /** 1a.8 真实测试修复（2026-07-18）：Spring @ConfigurationProperties 需要 setter 才能绑定嵌套对象。 */
        public void setMinimax(Provider minimax) { this.minimax = minimax; }
        public Provider getDoubao() { return doubao; }
        /** 1a.8 真实测试修复（2026-07-18）：同上。 */
        public void setDoubao(Provider doubao) { this.doubao = doubao; }
    }

    // ==========================================================
    // Text（1a.7 flat + 1a.8.3 Fallback inner class body）：minimax M3 (primary) + DeepSeek V3 (fallback)
    // ==========================================================
    /**
     * 文本 AI 客户端配置（1a.7 flat + 1a.8 补全 Fallback inner class）。
     */
    public static class Text {
        private String baseUrl = "https://api.minimaxi.com/v1";
        private String model = "MiniMax-Text-01";
        private double temperature = 0.7;
        private int maxTokens = 1024;
        private int timeoutSeconds = 60;
        private String apiKey;
        private Fallback fallback;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Fallback getFallback() { return fallback; }
        public void setFallback(Fallback fallback) { this.fallback = fallback; }
    }

    /**
     * 文本 AI Fallback 配置（1a.8 DeepSeek V3）。
     */
    public static class Fallback {
        private String baseUrl;
        private String model;
        private String apiKey;
        private int timeoutSeconds = 60;
        private double temperature = 0.7;
        private int maxTokens = 1024;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    // ==========================================================
    // Router（1a.8 新增）：imageCount 阈值 + cache + retry/CB 配置
    // ==========================================================
    /**
     * AiRouter 策略配置（1a.8 新增）。
     */
    public static class Router {
        private int imageCountThreshold = 2;
        private int cacheMaxSize = 1024;
        private int cacheTtlHours = 24;
        private int visionRetryAttempts = 2;
        private int visionCircuitBreakerSlidingWindow = 10;
        private double visionCircuitBreakerFailureRate = 50.0;

        public int getImageCountThreshold() { return imageCountThreshold; }
        public void setImageCountThreshold(int imageCountThreshold) { this.imageCountThreshold = imageCountThreshold; }
        public int getCacheMaxSize() { return cacheMaxSize; }
        public void setCacheMaxSize(int cacheMaxSize) { this.cacheMaxSize = cacheMaxSize; }
        public int getCacheTtlHours() { return cacheTtlHours; }
        public void setCacheTtlHours(int cacheTtlHours) { this.cacheTtlHours = cacheTtlHours; }
        public int getVisionRetryAttempts() { return visionRetryAttempts; }
        public void setVisionRetryAttempts(int visionRetryAttempts) { this.visionRetryAttempts = visionRetryAttempts; }
        public int getVisionCircuitBreakerSlidingWindow() { return visionCircuitBreakerSlidingWindow; }
        public void setVisionCircuitBreakerSlidingWindow(int v) { this.visionCircuitBreakerSlidingWindow = v; }
        public double getVisionCircuitBreakerFailureRate() { return visionCircuitBreakerFailureRate; }
        public void setVisionCircuitBreakerFailureRate(double v) { this.visionCircuitBreakerFailureRate = v; }
    }

    // ==========================================================
    // Provider（1a.8 简化重复）：vision 单 provider 配置子段
    // ==========================================================
    /**
     * 单 provider 配置（minimax/doubao 共用）。
     *
     * <p>1a.10 加 {@code apiStyle} 字段：豆包可显式选 {@code OPENAI_CHAT}（走 /chat/completions），
     * 不填则用 {@code OPENAI_RESPONSES}（默认 /responses）。minimax 永远用 OPENAI_CHAT。
     */
    public static class Provider {
        private String baseUrl = "https://api.minimaxi.com/v1";
        private String model;
        private int timeoutSeconds = 60;
        private String apiKey;
        private double temperature = 0.7;
        private int maxTokens = 1024;
        /** 1a.10：单 provider 的 ApiStyle 覆盖（豆包可设 OPENAI_CHAT；minimax 忽略此字段）。 */
        private ApiStyle apiStyle;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public ApiStyle getApiStyle() { return apiStyle; }
        public void setApiStyle(ApiStyle apiStyle) { this.apiStyle = apiStyle; }
    }
}

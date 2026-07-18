package com.fincontrol.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 客户端配置（[ai-client-split.md](#)，1a.8 升级）。
 *
 * <p>1a.8 起统一为 1 个根配置 {@code fincontrol.ai.*}，包含：
 * <ul>
 *   <li>{@code fincontrol.ai.vision} — 视觉模型（minimax primary + 豆包 OPENAI_RESPONSES fallback）—— 1a.8 重构</li>
 *   <li>{@code fincontrol.ai.text} — 文本模型（minimax M3 primary + DeepSeek fallback）—— 1a.7 flat + 1a.8 加 Fallback inner class body</li>
 *   <li>{@code fincontrol.ai.router} — 路由策略（imageCountThreshold + cache + retry/CB 配置）—— 1a.8 新增</li>
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
 * <p>兼容说明：1a.8 之前 VisionModelClient 用 {@code @Value("${fincontrol.vision.*}")} 注入；
 * 1a.8 B 阶段重构后改为 {@code fincontrol.ai.vision.minimax.*} + {@code fincontrol.ai.vision.doubao.*}
 * 双段共享同一个 Provider inner class。旧 {@code fincontrol.vision.*} 段保留作为 fallback，
 * 启动日志里提示「已迁移」。
 */
@ConfigurationProperties(prefix = "fincontrol.ai")
public class AiProperties {

    // 1a.8 新增
    private Vision vision = new Vision();

    // 1a.7 已存在（flat 字段，TextAiClient 当前依赖）；Commit C 时迁到 primary/fallback 双段
    private Text text = new Text();

    // 1a.8 新增
    private Router router = new Router();

    public Vision getVision() { return vision; }
    public void setVision(Vision vision) { this.vision = vision; }

    public Text getText() { return text; }
    public void setText(Text text) { this.text = text; }

    public Router getRouter() { return router; }
    public void setRouter(Router router) { this.router = router; }

    // ==========================================================
    // Vision（1a.8 新增）：minimax M3 (primary) + 豆包 OPENAI_RESPONSES (fallback)
    // ==========================================================
    /**
     * 视觉模型配置（1a.8 重构）。
     * <p>对应 {@code fincontrol.ai.vision.*}，包含 minimax primary 与豆包 fallback 双 provider。
     * 与 {@code fincontrol.ai.text.*} 完全隔离（独立 OkHttpClient 实例、连接池不互阻塞）。
     * <p>Commit B 阶段 VisionModelClient 改造时启用 — 当前 Commit A 阶段先建好配置段不破坏现状。
     */
    public static class Vision {
        /** minimax vision primary（OpenAI 兼容 chat completions）。 */
        private Provider minimax = new Provider();
        /** 豆包 vision fallback（OpenAI Responses API）。 */
        private Provider doubao = new Provider();

        public Provider getMinimax() { return minimax; }
        public void setMinimax(Provider minimax) { this.minimax = minimax; }
        public Provider getDoubao() { return doubao; }
        public void setDoubao(Provider doubao) { this.doubao = doubao; }
    }

    // ==========================================================
    // Text（1a.7 flat + 1a.8.3 Fallback inner class body）：minimax M3 (primary) + DeepSeek V3 (fallback)
    // ==========================================================
    /**
     * 文本 AI 客户端配置子段（1a.7 flat 起，1a.8 补全 Fallback inner class）。
     * <p>对应 {@code fincontrol.ai.text.*}，与 {@code fincontrol.ai.vision.*} 完全隔离。
     * <p>Commit C 阶段 TextAiClient 切换为从 {@code primary}/{@code fallback} 双 Provider 取值；
     * 现 Commit A 阶段保持 1a.7 flat 字段（{@link #getBaseUrl()} / {@link #getModel()} / ...）让 TextAiClient 不破坏编译。
     */
    public static class Text {
        // 1a.7 flat 字段（保留向后兼容，Commit C 迁到 primary/fallback 双段）
        private String baseUrl = "https://api.minimaxi.com/v1";
        private String model = "MiniMax-Text-01";
        private double temperature = 0.7;
        private int maxTokens = 1024;
        private int timeoutSeconds = 60;
        private String apiKey;
        // 1a.8.3：fallback inner class 占位（Commit A 补全 body）
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
     * 文本 AI Fallback 配置（1a.8.3 占位补全 body）。
     * <p>对应 {@code fincontrol.ai.text.fallback.*}，与 primary 段结构一致（OpenAI 兼容）。
     * <p>Commit C 阶段 TextAiClient 启用 — 当前 Commit A 阶段先建好配置段不破坏现状。
     */
    public static class Fallback {
        /** DeepSeek（或其他 OpenAI 兼容 provider）baseUrl。 */
        private String baseUrl;
        /** DeepSeek 模型名，如 {@code deepseek-chat}。 */
        private String model;
        /** API key。从 env 优先（{@code DEEPSEEK_CHAT_API_KEY}）；缺失时填 REPLACE_ME_DEEPSEEK_API_KEY 占位。 */
        private String apiKey;
        /** OkHttpClient read timeout（秒）。 */
        private int timeoutSeconds = 60;
        /** temperature（与 primary 保持一致，避免推断结果不稳定）。 */
        private double temperature = 0.7;
        /** maxTokens（与 primary 保持一致）。 */
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
     * <p>对应 {@code fincontrol.ai.router.*}，由 {@link AiRouter} 读取。
     * <p>默认值与 {@code work plan §Gate 0 决策} 保持一致：
     * <ul>
     *   <li>{@code imageCountThreshold = 2}（≤2 走 minimax primary；≥3 走豆包 primary）</li>
     *   <li>{@code cacheMaxSize = 1024}（Caffeine 缓存条目上限）</li>
     *   <li>{@code cacheTtlHours = 24}（同图缓存有效期）</li>
     *   <li>{@code visionRetryAttempts = 2}（resilience4j @Retry 重试次数）</li>
     *   <li>{@code visionCircuitBreakerSlidingWindow = 10}</li>
     * </ul>
     */
    public static class Router {
        /** vision 路由 imageCount 阈值：≤ threshold 走 minimax；> threshold 走豆包。 */
        private int imageCountThreshold = 2;
        /** Caffeine vision cache max size。 */
        private int cacheMaxSize = 1024;
        /** Caffeine vision cache TTL（小时）。 */
        private int cacheTtlHours = 24;
        /** resilience4j @Retry 重试次数（含首次 = retryAttempts+1 次调用）。 */
        private int visionRetryAttempts = 2;
        /** resilience4j @CircuitBreaker slidingWindow 大小。 */
        private int visionCircuitBreakerSlidingWindow = 10;
        /** resilience4j @CircuitBreaker failureRateThreshold（百分比）。 */
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
    // Provider（1a.8 新增）：统一的单 provider 配置子段（minimax/doubao/deepseek 共用）
    // ==========================================================
    /**
     * 单 provider 配置（1a.8 简化重复）。
     * <p>适用于 vision 双段（minimax/doubao）；Commit C 时 Text 也迁到这类（统一抽象）。
     * <p>缺 {@code apiKey} 时首调抛 5001；缺 {@code baseUrl}/{@code model} 时启动校验抛 5001。
     */
    public static class Provider {
        /** OpenAI-compatible 形态的 baseUrl。vision 可为 OpenAI /chat/completions 或豆包 /responses。 */
        private String baseUrl = "https://api.minimaxi.com/v1";
        /** 模型名；vision 端 minimax 为 MiniMax-M3、doubao 为 doubao-seed-1-8-251228。 */
        private String model;
        /** OkHttpClient read timeout（秒）。vision 多模态需要 1-3 分钟。 */
        private int timeoutSeconds = 60;
        /** API key。从 env 优先；缺失时填 REPLACE_ME_*_KEY 占位（启动不抛，首调抛 5001/5002）。 */
        private String apiKey;
        /** temperature（仅 text 端有效，vision 端忽略）。 */
        private double temperature = 0.7;
        /** maxTokens（仅 text 端有效，vision 端忽略）。 */
        private int maxTokens = 1024;

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
    }
}

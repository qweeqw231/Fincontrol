package com.fincontrol.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 客户端配置（[ai-client-split.md](#)）。
 *
 * <p>绑定 {@code fincontrol.ai.*} 配置段；当前只暴露 text 子段，vision 仍由
 * 既有 {@code fincontrol.vision.*}（{@code VisionModelClient}）独立维护，避免重构风险。
 *
 * <p>设计原则：
 * <ul>
 *   <li>视觉与文本 Bean 各自独立的配置前缀（{@code fincontrol.vision.*} vs {@code fincontrol.ai.text.*}）</li>
 *   <li>视觉与文本 Bean 各自独立的 OkHttpClient 实例（连接池不互相阻塞）</li>
 *   <li>视觉与文本 Bean 各自独立的 Prompt 资源（classpath / DB）</li>
 * </ul>
 *
 * <p>Phase 1 暂不重构 vision 端；待 Phase 2 评估统一为 {@code @ConfigurationProperties}。
 */
@ConfigurationProperties(prefix = "fincontrol.ai")
public class AiProperties {

    private Text text = new Text();

    public Text getText() {
        return text;
    }

    public void setText(Text text) {
        this.text = text;
    }

    /**
     * 文本 AI 客户端配置子段。
     * <p>对应 {@code fincontrol.ai.text.*}，与 {@code fincontrol.vision.*} 完全隔离。
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
     * Fallback config（1a.8 增补）。
     * <p>当

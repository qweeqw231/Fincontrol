package com.fincontrol.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 客户端装配（[ai-client-split.md](#)）。
 *
 * <p>注册 {@link AiProperties} 为 Spring Bean，并通过 {@link #textAiClient(AiProperties.Text)}
 * 暴露 {@link TextAiClient} Bean。
 *
 * <p>为何不用 {@code @Service} 注解 TextAiClient：
 * <ol>
 *   <li>需要从 {@code AiProperties} 中取子段（{@code text}），构造器参数更清晰</li>
 *   <li>测试时可显式注入自定义 OkHttpClient + ObjectMapper（[2026-07-17_phase1a6-acceptance-plan.md](#)）</li>
 *   <li>与 {@code VisionModelClient} 的 {@code @Service + @Value} 风格保持一致（不同点：text 端走 {@code @ConfigurationProperties}）</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public TextAiClient textAiClient(AiProperties properties) {
        return new TextAiClient(properties.getText());
    }
}
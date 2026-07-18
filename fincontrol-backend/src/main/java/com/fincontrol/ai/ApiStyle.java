package com.fincontrol.ai;

/**
 * AI provider API 形态枚举（1a.8 新增）。
 *
 * <p>决定 {@code VisionModelClient.callRaw(...)} 调用哪种 HTTP schema：
 * <ul>
 *   <li>{@link #OPENAI_CHAT} — OpenAI 兼容 {@code /chat/completions}（minimax 默认）</li>
 *   <li>{@link #OPENAI_RESPONSES} — OpenAI 新版 {@code /responses}（豆包 ARK 自家端点）</li>
 * </ul>
 *
 * <p>1a.8 路由策略（{@code AiRouter}）按 {@code imageCount} 选 primary：
 * <ul>
 *   <li>≤ 2 张图：{@code minimax} primary（OPENAI_CHAT）</li>
 *   <li>≥ 3 张图：{@code doubao} primary（OPENAI_RESPONSES）</li>
 * </ul>
 * 互为 fallback（HTTP 5xx/429/超时 → 切另一个 provider）。
 *
 * <p>文本端（chat）不区分 ApiStyle — minimax / DeepSeek 都是 OpenAI 兼容 {@code /chat/completions}。
 *
 * @author Liu Bocheng
 */
public enum ApiStyle {
    /** OpenAI {@code /chat/completions}（message[] + image_url）。minimax vision 用此。 */
    OPENAI_CHAT,
    /** OpenAI 新版 {@code /responses}（input[] + input_image）。豆包 ARK 自家端点。 */
    OPENAI_RESPONSES
}

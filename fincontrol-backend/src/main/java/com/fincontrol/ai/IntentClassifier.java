package com.fincontrol.ai;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.service.PromptLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 投资意图分类器（[ai-client-split.md §3.3](#) 〔v1.0〕）。
 *
 * <p>独立 Bean（区别于 {@link TextAiClient}）：
 * <ul>
 *   <li>复用 {@link TextAiClient} 客户端（共享 OkHttp 实例）</li>
 *   <li>复用 {@link PromptLoaderService} 加载 {@code intent_classifier} 提示词</li>
 *   <li>本类硬编码 {@code temperature=0.1}（低温度保分类稳定；与 {@link TextAiClient} 0.7 隔离）</li>
 * </ul>
 *
 * <p>输出：
 * <ul>
 *   <li>{@code "true"} → true（投资决策类）</li>
 *   <li>{@code "false"} → false（非投资决策类）</li>
 *   <li>其他（防御性）→ false + log warn</li>
 * </ul>
 *
 * <p>设计选择（[2026-07-17_phase1a6-work-plan.md §1 G2](#)）：
 * <ul>
 *   <li>本类只关心"是否投资决策类"，路由到 main_loop / garbage_loop 由调用方（{@code ChatService}）决策</li>
 *   <li>systemPrompt=null 调 {@link TextAiClient#chat}，few-shot 示例 + 用户输入 inline 拼成 user 消息（匹配 db-schema.sql §6 intent_classifier 现有 prompt 模板）</li>
 * </ul>
 */
@Service
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private final TextAiClient textAiClient;
    private final PromptLoaderService promptLoader;

    public IntentClassifier(TextAiClient textAiClient, PromptLoaderService promptLoader) {
        this.textAiClient = textAiClient;
        this.promptLoader = promptLoader;
    }

    /**
     * 判定用户输入是否属于"投资决策咨询"。
     *
     * @param userMessage 用户明文输入
     * @return true = 投资决策类（→ main_loop）；false = 其他（→ garbage_loop）
     * @throws BusinessException 5001 prompt 缺失或 AI 调用失败（继承自 TextAiClient）
     */
    public boolean isInvestmentRelated(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            // 空输入视为非投资类（路由到垃圾回路 + 闲聊回应）
            return false;
        }
        String systemPrompt = promptLoader.get("intent_classifier");
        // prompt 模板里包含"用户输入：[用户输入文本]"占位符；按 db-schema.sql §6 设计 inline 替换
        String fullInput = systemPrompt + "\n\n用户输入：" + userMessage;

        // 调 TextAiClient；systemPrompt=null 走"无系统提示"路径（与 garbage_loop 类似）
        String response = textAiClient.chat(null, fullInput);

        return parseBoolean(response);
    }

    /**
     * 严格解析模型响应。允许前后空白（{@code trim()}），其余字符必须严格等于 {@code "true"} / {@code "false"}。
     *
     * <p>防御性：模型偶尔会返回 {@code "True."} / {@code "false\n"} / {@code "YES"} 等，本方法一律视为 false。
     */
    static boolean parseBoolean(String response) {
        if (response == null) {
            log.warn("IntentClassifier 收到 null 响应，默认 false");
            return false;
        }
        String trimmed = response.trim();
        if ("true".equals(trimmed)) {
            return true;
        }
        if ("false".equals(trimmed)) {
            return false;
        }
        log.warn("IntentClassifier 收到非法布尔值: '{}'，默认 false", trimmed);
        return false;
    }
}
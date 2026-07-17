package com.fincontrol.ai;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.service.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.6 Slice A：A6-INT-03 〔IntentClassifier 业务测试〕。
 *
 * <p>替身：TextAiClient + PromptLoaderService mock。
 *
 * <p>覆盖：
 * <ul>
 *   <li>正常 "true" → true</li>
 *   <li>正常 "false" → false</li>
 *   <li>前后空白容忍（trim）</li>
 *   <li>大小写严格（"True"/"FALSE" → false 防御性）</li>
 *   <li>非布尔响应 → false（防御性）</li>
 *   <li>空 userMessage → false（不调 TextAiClient）</li>
 *   <li>调 TextAiClient 时 systemPrompt=null（垃圾回路 path）</li>
 *   <li>TextAiClient 抛 3001/3002 → 透传给上层</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntentClassifierTest {

    @Mock
    private TextAiClient textAiClient;

    @Mock
    private PromptLoaderService promptLoader;

    private IntentClassifier classifier;

    private static final String INTENT_PROMPT = "判断用户输入是否属于'投资决策咨询'。\n正例：...\n反例：...\n用户输入：[用户输入文本]";

    @BeforeEach
    void setUp() {
        classifier = new IntentClassifier(textAiClient, promptLoader);
        when(promptLoader.get("intent_classifier")).thenReturn(INTENT_PROMPT);
    }

    // ========================================================================
    // 正常路径
    // ========================================================================

    @Test
    @DisplayName("响应 'true' → isInvestmentRelated=true")
    void isInvestmentRelated_returnsTrue() {
        when(textAiClient.chat(isNull(), any())).thenReturn("true");

        boolean result = classifier.isInvestmentRelated("本月应该补仓多少");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("响应 'false' → isInvestmentRelated=false")
    void isInvestmentRelated_returnsFalse() {
        when(textAiClient.chat(isNull(), any())).thenReturn("false");

        boolean result = classifier.isInvestmentRelated("今天天气怎么样");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("响应 'true' 前后允许空白（trim）")
    void isInvestmentRelated_trimsWhitespace() {
        when(textAiClient.chat(isNull(), any())).thenReturn("  true  \n");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("响应 'false' 前后允许空白（trim）")
    void isInvestmentRelated_trimsWhitespaceFalse() {
        when(textAiClient.chat(isNull(), any())).thenReturn("\nfalse\n");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isFalse();
    }

    // ========================================================================
    // 防御性
    // ========================================================================

    @Test
    @DisplayName("响应 'True'（大写）→ false（严格小写）")
    void isInvestmentRelated_strictLowercase() {
        when(textAiClient.chat(isNull(), any())).thenReturn("True");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("响应 'FALSE'（大写）→ false（严格小写）")
    void isInvestmentRelated_strictLowercaseFalse() {
        when(textAiClient.chat(isNull(), any())).thenReturn("FALSE");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("响应 'true.'（带句号）→ false（防御性）")
    void isInvestmentRelated_rejectsWithPunctuation() {
        when(textAiClient.chat(isNull(), any())).thenReturn("true.");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("响应 'YES' → false（防御性，不接受非 true/false）")
    void isInvestmentRelated_rejectsYes() {
        when(textAiClient.chat(isNull(), any())).thenReturn("YES");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("响应空字符串 → false（防御性）")
    void isInvestmentRelated_rejectsEmpty() {
        when(textAiClient.chat(isNull(), any())).thenReturn("");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("响应 'true false'（多词） → false（防御性）")
    void isInvestmentRelated_rejectsMultipleWords() {
        when(textAiClient.chat(isNull(), any())).thenReturn("true false");

        boolean result = classifier.isInvestmentRelated("test");

        assertThat(result).isFalse();
    }

    // ========================================================================
    // 输入边界
    // ========================================================================

    @Test
    @DisplayName("空 userMessage → false，不调 TextAiClient")
    void isInvestmentRelated_emptyUserMessage() {
        boolean result1 = classifier.isInvestmentRelated(null);
        assertThat(result1).isFalse();

        boolean result2 = classifier.isInvestmentRelated("");
        assertThat(result2).isFalse();

        boolean result3 = classifier.isInvestmentRelated("   ");
        assertThat(result3).isFalse();

        // 不调 TextAiClient
        verify(textAiClient, never()).chat(any(), any());
    }

    // ========================================================================
    // 调 TextAiClient 时入参
    // ========================================================================

    @Test
    @DisplayName("调 TextAiClient 时 systemPrompt=null（垃圾回路 path）")
    void isInvestmentRelated_callsTextAiClientWithNullSystem() {
        when(textAiClient.chat(isNull(), any())).thenReturn("true");

        classifier.isInvestmentRelated("本月应该补仓多少");

        // systemPrompt 必须为 null
        verify(textAiClient).chat(eq(null), any());
    }

    @Test
    @DisplayName("userMessage inline 拼到 prompt 模板（替换'用户输入：[用户输入文本]'占位符语义）")
    void isInvestmentRelated_promptTemplateInline() {
        when(textAiClient.chat(isNull(), any())).thenReturn("true");

        classifier.isInvestmentRelated("本月应该补仓多少");

        ArgumentCaptor<String> userContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(textAiClient).chat(isNull(), userContentCaptor.capture());
        String sent = userContentCaptor.getValue();

        // 验证 user 内容含 prompt 模板 + 用户输入
        assertThat(sent).contains(INTENT_PROMPT);
        assertThat(sent).contains("本月应该补仓多少");
        assertThat(sent).contains("\n\n用户输入：");
    }

    // ========================================================================
    // 错误透传
    // ========================================================================

    @Test
    @DisplayName("TextAiClient 抛 3001 → 透传（不吞错）")
    void isInvestmentRelated_propagates3001() {
        when(textAiClient.chat(isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.VISION_INVALID_JSON, "上游非 JSON"));

        assertThatThrownBy(() -> classifier.isInvestmentRelated("test"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_INVALID_JSON.getCode());
    }

    @Test
    @DisplayName("TextAiClient 抛 3002 → 透传")
    void isInvestmentRelated_propagates3002() {
        when(textAiClient.chat(isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.VISION_TIMEOUT, "上游超时"));

        assertThatThrownBy(() -> classifier.isInvestmentRelated("test"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.VISION_TIMEOUT.getCode());
    }

    @Test
    @DisplayName("PromptLoader 抛 5001 → 透传（缺 prompt 视为配置错误）")
    void isInvestmentRelated_promptLoaderError() {
        when(promptLoader.get("intent_classifier"))
                .thenThrow(new BusinessException(ErrorCode.DATABASE_ERROR, "读取 prompt 失败"));

        assertThatThrownBy(() -> classifier.isInvestmentRelated("test"))
                .isInstanceOf(BusinessException.class);
    }

    // ========================================================================
    // parseBoolean 白盒
    // ========================================================================

    @Test
    @DisplayName("parseBoolean(null) → false")
    void parseBoolean_null() {
        assertThat(IntentClassifier.parseBoolean(null)).isFalse();
    }

    @Test
    @DisplayName("parseBoolean: 接受精确 'true' / 'false'，其他一律 false")
    void parseBoolean_strict() {
        assertThat(IntentClassifier.parseBoolean("true")).isTrue();
        assertThat(IntentClassifier.parseBoolean("false")).isFalse();
        assertThat(IntentClassifier.parseBoolean(" true ")).isTrue();
        assertThat(IntentClassifier.parseBoolean(" FALSE ")).isFalse();
        // 防御
        assertThat(IntentClassifier.parseBoolean("1")).isFalse();
        assertThat(IntentClassifier.parseBoolean("0")).isFalse();
        assertThat(IntentClassifier.parseBoolean("yes")).isFalse();
        assertThat(IntentClassifier.parseBoolean("TrueFalse")).isFalse();
    }

}

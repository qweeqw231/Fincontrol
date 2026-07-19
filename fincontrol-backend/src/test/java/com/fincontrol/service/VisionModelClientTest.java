package com.fincontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 1a.10：不受控视觉模型响应中的完整资产 JSON 选择。 */
class VisionModelClientTest {

    private VisionModelClient client;

    @BeforeEach
    void setUp() {
        client = new VisionModelClient(new OkHttpClient(), new ObjectMapper());
    }

    @Test
    void extractFirstJsonObject_directJson_keepsCompatibility() {
        JsonNode result = client.extractFirstJsonObject(
                "{\"snapshot_date\":\"2026-07-19\",\"categories\":[]}");

        assertThat(result.path("snapshot_date").asText()).isEqualTo("2026-07-19");
        assertThat(result.path("categories").isArray()).isTrue();
    }

    @Test
    void extractFirstJsonObject_thinkMarkdownAndLocalFragments_selectsCompleteAssetRoot() {
        String raw = "<think>先给出局部对象 "
                + "{\"fund_name\":\"错误的局部对象\",\"amount\":1.00}，随后再汇总。</think>\n"
                + "## 解析结果\n```json\n"
                + "{\"snapshot_date\":\"2026-07-15\",\"total_asset\":7884.68,"
                + "\"categories\":["
                + "{\"category_name\":\"货币类\",\"funds\":["
                + "{\"fund_name\":\"中加货币E\",\"amount\":796.32}]},"
                + "{\"category_name\":\"余额类\",\"funds\":["
                + "{\"fund_name\":\"余额宝\",\"amount\":320.85,"
                + "\"holding_profit\":null,\"cumulative_profit\":1.89}]}],"
                + "\"matchedFunds\":[\"中加货币E\",\"余额宝\"]}\n```";

        JsonNode result = client.extractFirstJsonObject(raw);

        assertThat(result.path("total_asset").decimalValue()).isEqualByComparingTo("7884.68");
        assertThat(result.path("categories")).hasSize(2);
        assertThat(result.path("categories").get(1).path("funds").get(0)
                .path("fund_name").asText()).isEqualTo("余额宝");
    }

    @Test
    void extractFirstJsonObject_bracesInsideEscapedString_doNotBreakCandidateScan() {
        String raw = "说明 {不是JSON} 后输出："
                + "{\"snapshot_date\":\"2026-07-15\","
                + "\"note\":\"字符串内有 { brace } 和转义引号 \\\"ok\\\"\","
                + "\"categories\":[{\"category_name\":\"商品类\",\"funds\":["
                + "{\"fund_name\":\"国泰黄金ETF联接C\",\"amount\":564.86}]}]}";

        JsonNode result = client.extractFirstJsonObject(raw);

        assertThat(result.path("categories").get(0).path("funds").get(0)
                .path("amount").decimalValue()).isEqualByComparingTo("564.86");
        assertThat(result.path("note").asText()).contains("{ brace }").contains("\"ok\"");
    }

    @Test
    void extractFirstJsonObject_sameScore_prefersLaterFinalObject() {
        String raw = "示例 {\"snapshot_date\":\"2026-01-01\",\"categories\":[]} "
                + "最终 {\"snapshot_date\":\"2026-07-15\",\"categories\":[]}";

        JsonNode result = client.extractFirstJsonObject(raw);

        assertThat(result.path("snapshot_date").asText()).isEqualTo("2026-07-15");
    }

    @Test
    void extractFirstJsonObject_withoutJson_throws3001() {
        assertThatThrownBy(() -> client.extractFirstJsonObject("只有 Markdown，没有 JSON"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VISION_INVALID_JSON));
    }
}

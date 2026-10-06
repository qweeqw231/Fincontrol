package com.fincontrol.dto.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2a 校正 DTO 的 JSON 字段命名守护：
 * 契约（api-contract §5 / 2a 扩展）要求 vCurr/uHigh/eHigh/mMax 等键原样输出，
 * 防止 Jackson 默认命名策略把 "VCurr" 变成 "vcurr" 之类。
 */
class CorrectionDtoJsonNamesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("2a-J01 · 响应 DTO 键名：vCurr/eHigh/uHigh/mMax/vTotalSixCategories/fBefore")
    void responseJsonNames() throws Exception {
        MonthlyCalculateResponse monthly = MonthlyCalculateResponse.builder()
                .deltaMTheory(new BigDecimal("85.22"))
                .uMonetaryDca(new BigDecimal("0"))
                .uMonetaryDcaTheory(new BigDecimal("82.40"))
                .eHigh(new BigDecimal("382"))
                .totalInvestment(new BigDecimal("1000"))
                .build();
        JsonNode m = JSON.readTree(JSON.writeValueAsString(monthly));
        assertThat(m.has("deltaMTheory")).isTrue();
        assertThat(m.has("vCurr")).isFalse(); // 该 DTO 无此字段，占位防误改
        assertThat(m.has("uMonetaryDca")).isTrue();
        assertThat(m.has("uMonetaryDcaTheory")).isTrue();
        assertThat(m.has("eHigh")).isTrue();
        // 微实验：字段注解不得产生重复键（mangled）
        assertThat(m.has("ehigh")).isFalse();

        CorrectionDefaultsResponse defaults = new CorrectionDefaultsResponse();
        defaults.setVTotalSixCategories(new BigDecimal("9525.87"));
        defaults.setVMonetary(new BigDecimal("988.27"));
        defaults.setUHigh(new BigDecimal("618"));
        defaults.setUBondDcaTheory(new BigDecimal("123.60"));
        JsonNode d = JSON.readTree(JSON.writeValueAsString(defaults));
        assertThat(d.has("vTotalSixCategories")).isTrue();
        assertThat(d.has("vMonetary")).isTrue();
        assertThat(d.has("uHigh")).isTrue();
        assertThat(d.has("uBondDcaTheory")).isTrue();

        QuarterlyCalculateResponse quarterly = QuarterlyCalculateResponse.builder()
                .vCurr(new BigDecimal("9525.87"))
                .vHighVol(new BigDecimal("7051.23"))
                .chosen(QuarterlyCalculateResponse.Chosen.builder()
                        .eHigh(new BigDecimal("382"))
                        .deltaMRaw(new BigDecimal("85.22"))
                        .build())
                .params(QuarterlyCalculateResponse.Params.builder()
                        .mMax(new BigDecimal("1000"))
                        .mMaxSource("manual")
                        .uHigh(new BigDecimal("618"))
                        .uMonetaryDca(new BigDecimal("0"))
                        .build())
                .icDrr(QuarterlyCalculateResponse.IcDrr.builder()
                        .fBefore(new BigDecimal("21.41"))
                        .fAfter(new BigDecimal("6.97"))
                        .build())
                .build();
        JsonNode q = JSON.readTree(JSON.writeValueAsString(quarterly));
        assertThat(q.has("vCurr")).isTrue();
        assertThat(q.has("vHighVol")).isTrue();
        assertThat(q.get("chosen").has("eHigh")).isTrue();
        assertThat(q.get("chosen").has("deltaMRaw")).isTrue();
        assertThat(q.get("params").has("mMax")).isTrue();
        assertThat(q.get("params").has("mMaxSource")).isTrue();
        assertThat(q.get("params").has("uHigh")).isTrue();
        assertThat(q.get("icDrr").has("fBefore")).isTrue();
        assertThat(q.get("icDrr").has("fAfter")).isTrue();

        QuarterlyDefaultsResponse qd = new QuarterlyDefaultsResponse();
        qd.setVCurr(new BigDecimal("9525.87"));
        qd.setUHighDefault(new BigDecimal("618"));
        JsonNode qdn = JSON.readTree(JSON.writeValueAsString(qd));
        assertThat(qdn.has("vCurr")).isTrue();
        assertThat(qdn.has("uHighDefault")).isTrue();
    }

    @Test
    @DisplayName("2a-J02 · 请求 DTO 反序列化：vCurr/uHigh/pMonetary/eHigh/mMax 原样识别")
    void requestJsonNames() throws Exception {
        MonthlyCalculateRequest mc = JSON.readValue(
                "{\"vCurr\":9525.87,\"vMonetary\":988.27,\"vBond\":1486.37,"
                        + "\"uHigh\":618,\"pMonetary\":10,\"pBond\":15,\"uMonetaryDca\":0,\"eHigh\":382}",
                MonthlyCalculateRequest.class);
        assertThat(mc.getVCurr()).isEqualByComparingTo("9525.87");
        assertThat(mc.getVMonetary()).isEqualByComparingTo("988.27");
        assertThat(mc.getUHigh()).isEqualByComparingTo("618");
        assertThat(mc.getPMonetary()).isEqualByComparingTo("10");
        assertThat(mc.getUMonetaryDca()).isEqualByComparingTo("0");
        assertThat(mc.getEHigh()).isEqualByComparingTo("382");

        QuarterlyCalculateRequest qc = JSON.readValue(
                "{\"vCurr\":9525.87,\"surplus\":700,\"mMax\":1000,\"uHigh\":618,"
                        + "\"mode\":\"mMaxCapped\",\"probeBaseMmax\":1260}",
                QuarterlyCalculateRequest.class);
        assertThat(qc.getVCurr()).isEqualByComparingTo("9525.87");
        assertThat(qc.getMMax()).isEqualByComparingTo("1000");
        assertThat(qc.getUHigh()).isEqualByComparingTo("618");
        assertThat(qc.getProbeBaseMmax()).isEqualByComparingTo("1260");

        MonthlyConfirmRequest confirm = JSON.readValue(
                "{\"vCurr\":9525.87,\"vHighVol\":7051.23,\"uHigh\":618,\"uBondDca\":123.6}",
                MonthlyConfirmRequest.class);
        assertThat(confirm.getVCurr()).isEqualByComparingTo("9525.87");
        assertThat(confirm.getVHighVol()).isEqualByComparingTo("7051.23");
        assertThat(confirm.getUHigh()).isEqualByComparingTo("618");
        assertThat(confirm.getUBondDca()).isEqualByComparingTo("123.6");
    }
}
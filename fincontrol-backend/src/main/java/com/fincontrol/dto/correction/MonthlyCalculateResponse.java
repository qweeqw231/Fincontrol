package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 2a `POST /api/correction/monthly/calculate` 响应体（[api-contract.md §5.2](#)）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCalculateResponse {

    /** 货币类理论补仓（方程组精确解） */
    private BigDecimal deltaMTheory;

    /** 固收类理论补仓（方程组精确解） */
    private BigDecimal deltaBTheory;

    /** 低波货币定投份额（求解实际使用值；未传时为 0） */
    @JsonProperty("uMonetaryDca")
    private BigDecimal uMonetaryDca;

    /** 低波固收定投份额（求解实际使用值；未传时为 0） */
    @JsonProperty("uBondDca")
    private BigDecimal uBondDca;

    /** 低波货币定投份额理论反推值（U_high × p_m/(1-p_m-p_b)） */
    @JsonProperty("uMonetaryDcaTheory")
    private BigDecimal uMonetaryDcaTheory;

    /** 低波固收定投份额理论反推值（U_high × p_b/(1-p_m-p_b)） */
    @JsonProperty("uBondDcaTheory")
    private BigDecimal uBondDcaTheory;

    /** 总投入（触发时含取整后补仓；未触发时仅定投与高波校正） */
    private BigDecimal totalInvestment;

    /** Δm 是否达到 purchaseThreshold 触发 ZOH 补仓 */
    private Boolean zohTriggered;

    /** 本次求解使用的 E_high */
    @JsonProperty("eHigh")
    private BigDecimal eHigh;

    private RoundingSuggestion roundingSuggestion;

    private List<WarningItem> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundingSuggestion {
        /** 货币类取整建议值 */
        private BigDecimal deltaMSuggested;
        /** 固收类取整建议值 */
        private BigDecimal deltaBSuggested;
        /** 取整策略：round_up_10 / round_up_1 / none */
        private String roundingStrategy;
        /** 取整后货币类偏差（百分点） */
        private BigDecimal deviationMSuggested;
        /** 取整后固收类偏差（百分点） */
        private BigDecimal deviationBSuggested;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarningItem {
        /** 结构化警告类型：exceed_budget_limit / zoh_not_triggered / low_vol_target_invalid */
        private String type;
        private String message;
    }
}
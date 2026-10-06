package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 2a `POST /api/correction/monthly/calculate` 请求体（[api-contract.md §5.2](#)）。
 *
 * <p>契约必填字段全部保留；{@code uMonetaryDca/uBondDca/eHigh} 为 2a 扩展（默认 0）。
 * <p>低波定投份额若实际发生（如 6/30 联合校正的月初低波定投），应显式传入。
 */
@Data
public class MonthlyCalculateRequest {

    /** 六大类合计（不含余额类） */
    @JsonProperty("vCurr")
    private BigDecimal vCurr;

    /** 货币类市值 */
    @JsonProperty("vMonetary")
    private BigDecimal vMonetary;

    /** 固收类市值 */
    @JsonProperty("vBond")
    private BigDecimal vBond;

    /** 当月高波定投总额 */
    @JsonProperty("uHigh")
    private BigDecimal uHigh;

    /** 货币类目标比例（百分数，如 10） */
    @JsonProperty("pMonetary")
    private BigDecimal pMonetary;

    /** 固收类目标比例（百分数，如 15） */
    @JsonProperty("pBond")
    private BigDecimal pBond;

    /** 高波目标比例合计（百分数，如 75；用于低波份额理论反推） */
    @JsonProperty("pHigh")
    private BigDecimal pHigh;

    /** 月度预算上限 */
    private BigDecimal budgetLimit;

    /** ZOH 补仓触发阈值 */
    private BigDecimal purchaseThreshold;

    /** 2a 扩展：低波货币定投份额（元），默认 0 */
    @JsonProperty("uMonetaryDca")
    private BigDecimal uMonetaryDca;

    /** 2a 扩展：低波固收定投份额（元），默认 0 */
    @JsonProperty("uBondDca")
    private BigDecimal uBondDca;

    /** 2a 扩展：高波校正预算 E_high（元），默认 0；月度台通常为 0，联合校正时传入 */
    @JsonProperty("eHigh")
    private BigDecimal eHigh;
}
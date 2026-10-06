package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 2a `POST /api/correction/monthly/recalculate` 请求体（[api-contract.md §5.3](#)）。
 */
@Data
public class MonthlyRecalculateRequest {

    @JsonProperty("vCurr")
    private BigDecimal vCurr;

    @JsonProperty("vMonetary")
    private BigDecimal vMonetary;

    @JsonProperty("vBond")
    private BigDecimal vBond;

    @JsonProperty("uHigh")
    private BigDecimal uHigh;

    /** 用户修改后的货币类实际补仓 */
    private BigDecimal deltaMActual;

    /** 用户修改后的固收类实际补仓 */
    private BigDecimal deltaBActual;

    /** 低波货币定投份额实际值（默认 0） */
    @JsonProperty("uMonetaryDcaActual")
    private BigDecimal uMonetaryDcaActual;

    /** 低波固收定投份额实际值（默认 0） */
    @JsonProperty("uBondDcaActual")
    private BigDecimal uBondDcaActual;

    /** 高波校正预算 E_high（默认 0） */
    @JsonProperty("eHigh")
    private BigDecimal eHigh;

    /** 2a 扩展：预算上限（缺省时后端从 user_config.monthly_budget_limit 读取） */
    private BigDecimal budgetLimit;

    /** 2a 扩展：货币类目标比例（百分数，缺省 10） */
    @JsonProperty("pMonetary")
    private BigDecimal pMonetary;

    /** 2a 扩展：固收类目标比例（百分数，缺省 15） */
    @JsonProperty("pBond")
    private BigDecimal pBond;
}
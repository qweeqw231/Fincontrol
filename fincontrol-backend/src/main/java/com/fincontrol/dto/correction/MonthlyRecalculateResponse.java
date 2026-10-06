package com.fincontrol.dto.correction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 2a `POST /api/correction/monthly/recalculate` 响应体（[api-contract.md §5.3](#)）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyRecalculateResponse {

    /** 取整/修改后的新比例（canonical 名 → 百分数），仅含货币类/固收类 */
    private Map<String, BigDecimal> newRatios;

    /** 新偏差（百分点） */
    private Map<String, BigDecimal> deviations;

    /** 实际总投入 */
    private BigDecimal totalInvestmentActual;

    /** 是否超出预算上限 */
    private Boolean overBudgetLimit;
}
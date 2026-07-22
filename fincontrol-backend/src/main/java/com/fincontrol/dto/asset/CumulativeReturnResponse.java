package com.fincontrol.dto.asset;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 1b.2 累计收益率响应（决策 4 v2 / 口径 A / 2026-07-22）。
 * <p>{@code algorithm} 字段标识当前使用的算法，Phase 3 升级为 {@code phase3_dietz} / {@code phase3_xirr} 时同步更新。
 * <p>{@code available} 为 false 表示前端该用 fallback 渲染（如算法不可用时）。
 *
 * @param available              是否可用（true = 算法有结果，false = 占位 / 不可用）
 * @param algorithm              算法标识（{@code phase1_simple} / {@code phase3_dietz} / {@code phase3_xirr}）
 * @param totalCumulativeProfit  Σcumulative_profit（含余额类 / 口径 A）
 * @param totalAmount            Σamount（含余额类 / 口径 A）
 * @param returnRate             累计收益率（{@code totalCumulativeProfit / totalAmount}，totalAmount=0 时为 0）
 * @param message                可选备注（如占位时的 "Phase 3 上线"）
 */
@Data
@Builder
public class CumulativeReturnResponse {
    private Boolean available;
    private String algorithm;
    private BigDecimal totalCumulativeProfit;
    private BigDecimal totalAmount;
    private BigDecimal returnRate;
    private String message;
}

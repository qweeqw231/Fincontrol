package com.fincontrol.dto.asset;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 1b.2 累计 / 持有 收益响应（决策 4 v2 / 决策 25 v2 / 口径 A / 2026-07-22）。
 * <p>累计 vs 持有 的区别：
 * <ul>
 *   <li>累计收益（cumulative）：自该基金建仓以来所有盈亏总和（含已实现，累加到卖出时也记入）
 *   <li>持有收益（holding）：当前仍持仓的浮盈/亏（不含已实现，卖出时清零）
 * </ul>
 * <p>{@code algorithm} 字段标识当前使用的算法，Phase 3 升级为 {@code phase3_dietz} / {@code phase3_xirr} 时同步更新。
 * <p>{@code available} 为 false 表示前端该用 fallback 渲染（如算法不可用时）。
 *
 * @param available              是否可用（true = 算法有结果，false = 占位 / 不可用）
 * @param algorithm              算法标识（{@code phase1_simple} / {@code phase3_dietz} / {@code phase3_xirr}）
 * @param totalCumulativeProfit  Σcumulative_profit（含余额类 / 口径 A / 累计盈亏）
 * @param totalHoldingProfit     Σholding_profit（仅当前持仓的浮盈/亏，不含已实现）
 * @param totalAmount            Σamount（含余额类 / 口径 A）
 * @param returnRate             累计收益率（{@code totalCumulativeProfit / totalAmount}，totalAmount=0 时为 0）
 * @param holdingReturnRate      持有收益率（{@code totalHoldingProfit / totalAmount}，totalAmount=0 时为 0）
 * @param snapshotDate           实际计算用的 snapshot_date（最大且 is_latest=1 那天）
 * @param fundCount              is_latest=1 的基金行数（口径 A 全部含余额类）
 * @param message                可选备注（如占位时的 "Phase 3 上线"）
 */
@Data
@Builder
public class CumulativeReturnResponse {
    private Boolean available;
    private String algorithm;
    private BigDecimal totalCumulativeProfit;
    private BigDecimal totalHoldingProfit;
    private BigDecimal totalAmount;
    private BigDecimal returnRate;
    private BigDecimal holdingReturnRate;
    private String snapshotDate;
    private Integer fundCount;
    private String message;
}

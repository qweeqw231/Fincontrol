package com.fincontrol.dto.asset;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 1b.2 累计 + 持有 收益响应（决策 4 v2 + 决策 25 v3 / 2026-07-22）。
 * <p>累计 vs 持有 区别：
 * <ul>
 *   <li>累计（cumulative）：自该基金建仓以来所有盈亏总和（含已实现盈亏，如部分卖出后）
 *   <li>持有（holding）：当前仍持仓的浮盈/亏（不含已实现盈亏）
 * </ul>
 * <p>Phase 2 升级为 Modified Dietz / XIRR 时，{@link #algorithm} 从 {@code phase1_simple} 改为 {@code phase3_dietz} / {@code phase3_xirr}。
 * <p>{@link #available} 为 false 表示前端该用 fallback 渲染（如算法不可用时）。
 *
 * <h3>决策 25 v3：持有收益展示层 Smart Fallback</h3>
 * 余额宝原图 holding 字段为 NULL（1a 解析层尊重原图不补 0 也不补 cumulative），但累计 1.90 元。
 * 1b.2 展示层在 Service 端做"余额类 + holding IS NULL → fallback cumulative" 校正，不动数据层。
 * <ul>
 *   <li>{@link #rawHoldingProfit} = 原始 SUM(holding_profit)（如 -36.55）
 *   <li>{@link #balanceFundAdjustment} = 余额宝校正值（如 +1.90）
 *   <li>{@link #totalHoldingProfit} = 校正后 = raw + adjustment（如 -34.65）
 *   <li>{@link #balanceFundStatus} = 'normal' / 'included' / 'excluded_unknown'
 * </ul>
 *
 * @param available              是否可用（true = 算法有结果，false = 占位 / 不可用）
 * @param algorithm              算法标识（{@code phase1_simple} / {@code phase3_dietz} / {@code phase3_xirr}）
 * @param totalCumulativeProfit  Σcumulative_profit（含余额类 / 口径 A / 累计盈亏）
 * @param rawHoldingProfit        Σholding_profit 原值（未做余额宝 fallback）
 * @param balanceFundAdjustment  余额宝 fallback 调整值（holding NULL → 用 cumulative 替代）
 * @param totalHoldingProfit     Σholding_profit 校正后 = raw + adjustment
 * @param holdingReturnRate      持有收益率（{@code totalHoldingProfit / totalAmount}，totalAmount=0 时为 0）
 * @param totalAmount            Σamount（含余额类 / 口径 A）
 * @param returnRate             累计收益率（{@code totalCumulativeProfit / totalAmount}，totalAmount=0 时为 0）
 * @param balanceFundStatus      余额宝 fallback 状态：'normal' / 'included' / 'excluded_unknown'
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
    private BigDecimal rawHoldingProfit;
    private BigDecimal balanceFundAdjustment;
    private BigDecimal totalHoldingProfit;
    private BigDecimal totalAmount;
    private BigDecimal returnRate;
    private BigDecimal holdingReturnRate;
    private String balanceFundStatus;
    private String snapshotDate;
    private Integer fundCount;
    private String message;
}

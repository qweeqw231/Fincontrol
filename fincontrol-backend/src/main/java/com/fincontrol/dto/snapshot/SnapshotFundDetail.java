package com.fincontrol.dto.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 1a.4 快照基金明细行（[api-contract.md §3.2](#)）。
 *
 * <p>仅在 detail 模式返回。profit 严格来源 {@code asset_raw.profit}，
 * 不会从 {@code asset_snapshot} 推断。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotFundDetail {

    private String fundName;

    private BigDecimal amount;

    /** 1a.8.7 兼容期：与 holdingProfit 同步填充。 */
    private BigDecimal profit;

    /** 严格=持有收益（不含当日浮盈）。1a.8.7。 */
    private BigDecimal holdingProfit;

    /** 含已实现盈亏。1a.8.7。 */
    private BigDecimal cumulativeProfit;

    private String category;

    /** 1a.8.8：来自 FundCategoryResolver；true=user_correct，false=ai_guess/未命中。 */
    private Boolean isUserConfirmed;
}

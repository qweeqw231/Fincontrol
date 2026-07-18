package com.fincontrol.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 1a.4 余额类基金明细行（[api-contract.md §9.1](#)）。
 *
 * <p>所有金额来自 {@code asset_raw}，{@code category} 固定为"余额类"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetBalanceItem {

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

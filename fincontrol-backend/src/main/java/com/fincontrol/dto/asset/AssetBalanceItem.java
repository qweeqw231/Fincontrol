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

    private BigDecimal profit;

    private String category;
}

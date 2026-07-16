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

    private BigDecimal profit;

    private String category;
}

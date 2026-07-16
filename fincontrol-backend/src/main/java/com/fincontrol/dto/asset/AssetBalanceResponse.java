package com.fincontrol.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 1a.4 `GET /api/asset/balance` 响应体（[api-contract.md §9.1](#)）。
 *
 * <p>只统计 `category='余额类' AND is_latest=true`；空数据返回 balanceFundTotal=0 且 items=[]。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetBalanceResponse {

    private BigDecimal balanceFundTotal;

    private List<AssetBalanceItem> items;

    private LocalDate snapshotDate;
}

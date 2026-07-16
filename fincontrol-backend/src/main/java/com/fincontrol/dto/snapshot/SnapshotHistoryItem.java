package com.fincontrol.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 1a.4 `GET /api/snapshot/history` 列表项（[api-contract.md §3.4](#)）。
 *
 * <p>按用户/日期范围汇总每个快照日期，categoryCount 为该日期的分类数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotHistoryItem {

    private LocalDate snapshotDate;

    private BigDecimal sixCategoriesTotal;

    private BigDecimal balanceFund;

    private Integer categoryCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmedAt;
}

package com.fincontrol.dto.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 1a.4 `GET /api/snapshot/latest` 与 `GET /api/snapshot/latest/detail` 响应体（[api-contract.md §3.1/§3.2](#)）。
 *
 * <p>六类汇总来自 {@code asset_snapshot}，详情基金来自 {@code asset_raw}。
 * 余额类是否参与汇总与 totalAssetWithBalance 计算，由调用方通过入参控制。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotLatestResponse {

    /** 快照日期（user 最新一次有 is_latest=true 行的日期） */
    private LocalDate snapshotDate;

    /** 最新一次确认的时间，对应 asset_snapshot.updated_at */
    private LocalDateTime snapshotConfirmedAt;

    /** 6 大类合计（不含余额类） */
    private BigDecimal sixCategoriesTotal;

    /** 余额类金额 */
    private BigDecimal balanceFund;

    /** 6 大类合计 + 余额类 */
    private BigDecimal totalAssetWithBalance;

    /** 分类汇总；includeDetail=true 时 funds 非空 */
    private List<SnapshotCategorySummary> categories;
}

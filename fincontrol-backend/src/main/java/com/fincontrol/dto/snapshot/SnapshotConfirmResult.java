package com.fincontrol.dto.snapshot;

import com.fincontrol.service.DedupEngine.DedupReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 1a.3 confirm 响应体（[docs/phase-1/designs/1a3-dedup-strategy.md §4.2](#)）。
 *
 * <p>三层结构：
 * <ul>
 *   <li>资产写入统计（assetRawInserted / assetSnapshotUpserted）</li>
 *   <li>DedupEngine 报告（dedupReport）</li>
 *   <li>1a.3 自身警告（warnings）+ 1a.8 撤销钩子（rollbackAvailable / rollbackDeadline）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotConfirmResult {

    /** 实际写入 asset_raw 行数（fund 粒度） */
    private int assetRawInserted;

    /** 实际 upsert asset_snapshot 行数（category 粒度） */
    private int assetSnapshotUpserted;

    /** DedupEngine 报告（5 维 dedup 统计） */
    private DedupReport dedupReport;

    /** 1a.3 自身警告（如 [P0-1.5] 日期校验失败） */
    private List<String> warnings;

    /** 1a.8 撤销钩子：当前批 10s 内可撤销 */
    private boolean rollbackAvailable;

    /** 1a.8 撤销截止时间（= confirmedAt + 10s） */
    private LocalDateTime rollbackDeadline;
}

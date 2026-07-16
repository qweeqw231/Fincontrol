package com.fincontrol.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fincontrol.dto.screenshot.ParsedAsset;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 1a.3 confirm 请求体（[docs/phase-1/designs/1a3-dedup-strategy.md §4.1](#)）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotConfirmRequest {

    /** 用户 ID（来自 X-User-Id header；单用户 MVP 默认 1） */
    private Long userId;

    /** [P0-1.5] 快照日期 YYYY-MM-DD */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate snapshotDate;

    /** 用户备注（optional） */
    private String snapshotNote;

    /** 多张图合并输入（来自 1a.2 parse + 1a.4 parse-batch） */
    private List<ParsedAsset> parsedAssets;

    /** [P0-3.4] 单 fund 忽略（前端说"略过"） */
    private Boolean isIgnored;

    /** [P0-1.5] 同日已有快照时必须 true（用户已确认覆盖） */
    private Boolean confirmedOverwrite;

    /** [P0-3.3] 是否包含 余额类 */
    private Boolean includeBalance;
}

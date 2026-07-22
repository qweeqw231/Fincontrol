package com.fincontrol.dto.snapshot;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 1b.3.3 决策 27：POST /api/snapshot/set-current 请求 DTO。
 * <p>用户主动切换 is_current=true 指向的 snapshot_date。
 */
@Data
@NoArgsConstructor
public class SnapshotSetCurrentRequest {
    private Long userId;
    private LocalDate snapshotDate;
}

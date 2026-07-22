package com.fincontrol.dto.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 1b.3.3 决策 27：POST /api/snapshot/set-current 响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnapshotSetCurrentResult {
    private Long userId;
    private LocalDate previousCurrent;
    private LocalDate newCurrent;
    private String message;
}

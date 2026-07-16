package com.fincontrol.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 1a.4 最近操作 / 解析活动项（[api-contract.md §9.2](#)）。
 *
 * <p>{@code operationType} 在 Phase 1 固定为 {@code screenshot_parse}；
 * {@code summary} 由 {@link com.fincontrol.service.ParseLogQueryService} 派生。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationRecentItem {

    private String operationType;

    private LocalDateTime operationDate;

    private String summary;

    private LocalDate snapshotDate;
}

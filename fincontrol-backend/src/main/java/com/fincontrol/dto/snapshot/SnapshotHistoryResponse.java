package com.fincontrol.dto.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 1a.4 `GET /api/snapshot/history` 响应体（[api-contract.md §3.4](#)）。
 *
 * <p>items 按日期倒序；分页参数与全局分页规范一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotHistoryResponse {

    private List<SnapshotHistoryItem> items;

    private long total;

    private int page;

    private int pageSize;
}

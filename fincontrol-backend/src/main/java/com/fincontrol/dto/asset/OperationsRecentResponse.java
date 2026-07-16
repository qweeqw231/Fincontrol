package com.fincontrol.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 1a.4 `GET /api/asset/operations/recent` 响应体（[api-contract.md §9.2](#)）。
 *
 * <p>Phase 1 来源是当前 user 的 `chat_history` 解析活动，不宣称确认入库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsRecentResponse {

    private List<OperationRecentItem> items;
}

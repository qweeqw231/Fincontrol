package com.fincontrol.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 1a.5 批量查询映射的响应体（[api-contract.md §7.1](#)）。
 *
 * <p>按基金名称在 {@code fund_category_map} 中是否已存在拆为两组：
 * <ul>
 *   <li>{@code matchedFunds} — 命中，含当前大类 + 来源 + 确认时间</li>
 *   <li>{@code unmatchedFunds} — 未命中，仅基金名（前端用于渲染下拉预填）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMapMatchResponse {

    private List<CategoryMapMatchItem> matchedFunds;

    private List<String> unmatchedFunds;
}
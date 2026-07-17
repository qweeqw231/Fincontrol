package com.fincontrol.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 1a.5 批量查询映射的单条匹配项（[api-contract.md §7.1](#)）。
 *
 * <p>对应 {@code matchedFunds[]} 数组元素。{@code source} 标识该映射的来源：
 * <ul>
 *   <li>{@code ai_guess} — AI 解析阶段猜测</li>
 *   <li>{@code user_correct} — 用户纠正（含 1a.3 confirm 入库）</li>
 *   <li>{@code user_manual} — 用户手动新增（仅 1a.5 update）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMapMatchItem {

    private String fundName;

    private String category;

    /** ai_guess / user_correct / user_manual */
    private String source;

    private LocalDateTime confirmedAt;
}
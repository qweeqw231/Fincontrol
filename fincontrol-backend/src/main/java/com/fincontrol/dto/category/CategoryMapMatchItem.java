package com.fincontrol.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    /**
     * 1b4pr6b 决策 33 D7（R5）：上次有该基金的 confirm snapshot_date。
     * 前端 preview modal 用此字段判断是否在「消失-重现」状态。
     */
    private LocalDate lastSeenSnapshotDate;

    /**
     * 1b4pr6b 决策 33 D7（R5）：第一次发现该基金缺失的 confirm snapshot_date。
     * NULL = 当前未处于缺失状态。
     * 非 NULL = 之前消失过，前端需渲染「您可能于 t1 之前清仓」黄色 banner。
     */
    private LocalDate firstMissingSnapshotDate;
}

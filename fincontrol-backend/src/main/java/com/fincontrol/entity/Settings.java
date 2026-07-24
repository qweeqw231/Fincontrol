package com.fincontrol.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * settings 全局配置实体（PR3plus 决策 31）。
 *
 * <p>表结构：
 * <ul>
 *   <li>user_id (PK) — 用户 ID，单用户 MVP 默认 1</li>
 *   <li>max_snapshot_age_days — 历史截图限制天数
 *       <ul>
 *         <li>-1 = 不限制</li>
 *         <li>7/14/30/180 = 限定天数（前端可选项）</li>
 *       </ul>
 *   </li>
 *   <li>created_at / updated_at</li>
 * </ul>
 *
 * <p>见 [decision-31](../../../docs/phase-1/decisions/decision-31-settings-global-config-table.md)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Settings {

    private Long userId;

    /**
     * 历史截图限制天数。
     * -1 = 不限制；7/14/30/180 = 限定天数。
     */
    private Integer maxSnapshotAgeDays;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

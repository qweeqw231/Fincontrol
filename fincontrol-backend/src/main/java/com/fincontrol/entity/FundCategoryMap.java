package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金-大类映射表（[db-schema.sql §3](#)）。
 *
 * <p>按 {@code (user_id, fund_name)} 唯一索引 UPSERT。1a.3 confirm 写入
 * 基金确认入库类别（{@code source='user_correct'}）；1a.5 暴露
 * {@code GET /api/category-map/match} 给前端查表。
 */
@Data
@NoArgsConstructor
@TableName("fund_category_map")
public class FundCategoryMap {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("fund_name")
    private String fundName;

    @TableField("category")
    private String category;

    @TableField("source")
    private String source;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    /** 1a.8.8：最近一次出现在截图中的时间。stale 判定 + re-confirm 去弹窗。 */
    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    /**
     * 1b4pr6b 决策 33 D7（R5）：上次有该基金的 confirm snapshot_date。
     * 锚点计算 = MAX(last_seen_snapshot_date) for user_id。
     * 仅在 isAnchorUpdate（snapshotDate >= anchorDate）的 confirm 中更新。
     */
    @TableField("last_seen_snapshot_date")
    private LocalDate lastSeenSnapshotDate;

    /**
     * 1b4pr6b 决策 33 D7（R5）：第一次发现该基金缺失的 confirm snapshot_date。
     * 重现时（snapshotDate >= anchorDate 且 fund 重新出现）被清空。
     * 前端 preview modal 用此字段渲染「您可能于 t1 之前清仓」黄色 banner。
     */
    @TableField("first_missing_snapshot_date")
    private LocalDate firstMissingSnapshotDate;
}

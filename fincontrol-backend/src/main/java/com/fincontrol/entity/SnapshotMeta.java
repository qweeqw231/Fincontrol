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
 * 快照元数据表（决策 27：is_latest 双层语义 + 跨日期 is_current）。
 *
 * <p>每 (user_id, snapshot_date) 唯一一行：
 * <ul>
 *   <li>is_latest：per-date 标记（与 asset_raw.is_latest 语义一致）</li>
 *   <li>is_current：跨日期当前快照（每 user 最多 1 行 true）</li>
 *   <li>前端 UI 可手动切换 is_current</li>
 * </ul>
 *
 * <p>对应 DDL：[scripts/1b3/00-snapshot-meta.sql](../../../../../scripts/1b3/00-snapshot-meta.sql)。
 */
@Data
@NoArgsConstructor
@TableName("snapshot_meta")
public class SnapshotMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("snapshot_date")
    private LocalDate snapshotDate;

    /** per-date 标记（与 asset_raw.is_latest 同步） */
    @TableField("is_latest")
    private Boolean isLatest;

    /** 跨日期当前快照（每 user 最多 1 行 true） */
    @TableField("is_current")
    private Boolean isCurrent;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

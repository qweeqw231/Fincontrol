package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产快照汇总表（[db-schema.sql §2](#)）。
 *
 * <p>每 category 一行；写入时按 {@code (user_id, snapshot_date, category, is_latest=true)} 唯一索引 UPSERT（写入时 is_latest=true；同日重写时把旧批 is_latest=false 翻转）。
 */
@Data
@NoArgsConstructor
@TableName("asset_snapshot")
public class AssetSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("snapshot_date")
    private LocalDate snapshotDate;

    @TableField("category")
    private String category;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("target_ratio")
    private BigDecimal targetRatio;

    @TableField("actual_ratio")
    private BigDecimal actualRatio;

    @TableField("balance_fund")
    private BigDecimal balanceFund;

    @TableField("sub_detail")
    private String subDetail;

    @TableField("is_latest")
    private Boolean isLatest;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

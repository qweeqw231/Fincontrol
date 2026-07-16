package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 原始资产明细表（不可变，[db-schema.sql §1](#)）。
 *
 * <p>每只 fund 一行；写入后由 {@code 1a.3 SnapShotConfirmService} 触发；
 * 撤销走 {@code 1a.8 DELETE /api/snapshot/confirm/{id}} 把整批 is_latest=false。
 */
@Data
@NoArgsConstructor
@TableName("asset_raw")
public class AssetRaw {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("snapshot_date")
    private LocalDate snapshotDate;

    @TableField("fund_name")
    private String fundName;

    @TableField("fund_code")
    private String fundCode;

    @TableField("category")
    private String category;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("profit")
    private BigDecimal profit;

    @TableField("source")
    private String source;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("is_latest")
    private Boolean isLatest;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;
}

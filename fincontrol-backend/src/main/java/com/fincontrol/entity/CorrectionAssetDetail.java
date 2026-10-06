package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 校正逐资产明细表（2a 校正页，[db-schema.sql §7.2](#)）。
 *
 * <p>一行 = 一个阶段 × 一个类别；phase 枚举：pre_six/post_six/pre_high/post_high。
 */
@Data
@NoArgsConstructor
@TableName("correction_asset_detail")
public class CorrectionAssetDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("operation_log_id")
    private Long operationLogId;

    @TableField("sort_order")
    private Integer sortOrder;

    /** 阶段：pre_six=校正前六大类 / post_six=校正后六大类 / pre_high=高波内部校正前 / post_high=高波内部校正后 */
    @TableField("phase")
    private String phase;

    @TableField("category")
    private String category;

    /** 该阶段市值（元） */
    @TableField("amount")
    private BigDecimal amount;

    /** 实际占比（%）：six 阶段=占六大类；high 阶段=高波内部占比 */
    @TableField("ratio_actual")
    private BigDecimal ratioActual;

    /** 目标占比（%） */
    @TableField("ratio_target")
    private BigDecimal ratioTarget;

    /** 偏差（百分点，actual - target） */
    @TableField("deviation")
    private BigDecimal deviation;

    /** 本类补仓求解原值（元，未取整） */
    @TableField("delta_raw")
    private BigDecimal deltaRaw;

    /** 本类补仓执行值（元，取整后） */
    @TableField("delta_amount")
    private BigDecimal deltaAmount;

    @TableField("note")
    private String note;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
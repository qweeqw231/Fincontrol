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
 * 校正迭代轮次明细表（2a 校正页，[db-schema.sql §7.1](#)）。
 *
 * <p>一次校正的求解过程拆分为多轮（如 6/30 联合校正 α 七轮压缩），每轮一行。
 */
@Data
@NoArgsConstructor
@TableName("correction_iteration")
public class CorrectionIteration {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("operation_log_id")
    private Long operationLogId;

    /** 轮次顺序（0=初始，1..n=第 n 轮） */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 本轮高波校正预算占比 α（小数，如 0.2000 / 0.0419） */
    @TableField("alpha")
    private BigDecimal alpha;

    /** 本轮高波校正预算 E_high（元） */
    @TableField("e_high")
    private BigDecimal eHigh;

    /** 本轮低波货币补仓 Δm（元） */
    @TableField("delta_m")
    private BigDecimal deltaM;

    /** 本轮低波固收补仓 Δb（元） */
    @TableField("delta_b")
    private BigDecimal deltaB;

    /** 本轮 Δm 是否触发 ZOH 补仓（≥ purchaseThreshold） */
    @TableField("zoh_triggered")
    private Boolean zohTriggered;

    /** 本轮总投入（元） */
    @TableField("total_investment")
    private BigDecimal totalInvestment;

    /** 超出 M_max 金额（元）；NULL=未超限 */
    @TableField("over_limit")
    private BigDecimal overLimit;

    @TableField("note")
    private String note;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
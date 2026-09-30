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
 * 操作日志表（月度/季度校正流水，[db-schema.sql §7](#)）。
 * <p>Phase 3：外部导入的 ZOH/LQR 校正 + 战术调仓记录（source='import_mcf'）用于净值页事件时间线。
 */
@Data
@NoArgsConstructor
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("operation_date")
    private LocalDateTime operationDate;

    @TableField("operation_type")
    private String operationType;

    @TableField("snapshot_date")
    private LocalDate snapshotDate;

    @TableField("v_curr")
    private BigDecimal vCurr;

    @TableField("v_monetary")
    private BigDecimal vMonetary;

    @TableField("v_bond")
    private BigDecimal vBond;

    @TableField("v_high_vol")
    private BigDecimal vHighVol;

    @TableField("u_high")
    private BigDecimal uHigh;

    @TableField("delta_m_theory")
    private BigDecimal deltaMTheory;

    @TableField("delta_b_theory")
    private BigDecimal deltaBTheory;

    @TableField("delta_m_actual")
    private BigDecimal deltaMActual;

    @TableField("delta_b_actual")
    private BigDecimal deltaBActual;

    @TableField("rounding_strategy")
    private String roundingStrategy;

    @TableField("budget_limit_used")
    private BigDecimal budgetLimitUsed;

    @TableField("total_investment")
    private BigDecimal totalInvestment;

    @TableField("triggered_boundary")
    private String triggeredBoundary;

    @TableField("warnings")
    private String warnings;

    @TableField("source")
    private String source;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
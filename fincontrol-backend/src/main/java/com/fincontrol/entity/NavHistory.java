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
 * 每日净值历史表（外部数据导入，Phase 3 可视化）。
 */
@Data
@NoArgsConstructor
@TableName("nav_history")
public class NavHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("nav_date")
    private LocalDate navDate;

    @TableField("weekday")
    private String weekday;

    @TableField("daily_return_pct")
    private BigDecimal dailyReturnPct;

    @TableField("actual_profit")
    private BigDecimal actualProfit;

    @TableField("cumulative_profit")
    private BigDecimal cumulativeProfit;

    @TableField("nav")
    private BigDecimal nav;

    @TableField("nav_pct")
    private BigDecimal navPct;

    @TableField("total_asset")
    private BigDecimal totalAsset;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

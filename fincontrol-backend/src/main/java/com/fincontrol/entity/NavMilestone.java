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
 * 净值关键里程碑表（外部数据导入，Phase 3 可视化标注）。
 */
@Data
@NoArgsConstructor
@TableName("nav_milestone")
public class NavMilestone {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("milestone_date")
    private LocalDate milestoneDate;

    @TableField("category")
    private String category;

    @TableField("cumulative_profit")
    private BigDecimal cumulativeProfit;

    @TableField("nav")
    private BigDecimal nav;

    @TableField("nav_pct")
    private BigDecimal navPct;

    @TableField("description")
    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

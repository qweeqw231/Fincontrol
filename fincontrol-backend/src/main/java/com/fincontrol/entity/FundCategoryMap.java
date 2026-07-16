package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}

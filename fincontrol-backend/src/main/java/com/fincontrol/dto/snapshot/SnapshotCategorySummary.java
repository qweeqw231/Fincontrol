package com.fincontrol.dto.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 1a.4 快照分类汇总项（[api-contract.md §3.1](#)）。
 *
 * <p>latest 与 latest/detail 通用，仅 detail 额外包含 funds。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotCategorySummary {

    /** 分类名称（货币类/固收类/.../余额类） */
    private String categoryName;

    /** 该分类下基金合计金额（不含余额类时该值 = 6 大类总市值） */
    private BigDecimal categoryTotal;

    /** 实际占比 %，归一化到 100；余额类一般不参与 ratio */
    private BigDecimal actualRatio;

    /** 目标占比 %，从 user_config.target_ratios 读 */
    private BigDecimal targetRatio;

    /** 偏差 = actual - target，单位 % */
    private BigDecimal deviation;

    /** 当前分类下 is_latest=true 的基金行数 */
    private Integer fundCount;

    /** 详情模式：当前分类下所有基金明细（amount/profit/category），仅 detail 出现 */
    private List<SnapshotFundDetail> funds;
}

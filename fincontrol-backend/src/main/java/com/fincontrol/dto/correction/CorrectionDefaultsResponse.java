package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 2a `GET /api/correction/defaults` 响应体（[api-contract.md §5.1](#)）。
 *
 * <p>月度校正操作台默认值：来自当前 {@code is_current} 快照 + {@code user_config}。
 * <p>契约键名（vTotalSixCategories/uHigh 等）用 {@link JsonProperty} 固定，防止 Jackson
 * 默认命名策略走样。
 */
@Data
public class CorrectionDefaultsResponse {

    /** 六大类合计（不含余额类），= V_curr */
    @JsonProperty("vTotalSixCategories")
    private BigDecimal vTotalSixCategories;

    /** 货币类市值 */
    @JsonProperty("vMonetary")
    private BigDecimal vMonetary;

    /** 固收类市值 */
    @JsonProperty("vBond")
    private BigDecimal vBond;

    /** 高波合计市值（商品 + A股 + 海外 + 港股） */
    @JsonProperty("vHighVol")
    private BigDecimal vHighVol;

    /** 当月高波定投总额（user_config.high_vol_dca_budget） */
    @JsonProperty("uHigh")
    private BigDecimal uHigh;

    /** 低波定投份额理论反推值（U_high × p/(1-p_m-p_b)） */
    @JsonProperty("uMonetaryDcaTheory")
    private BigDecimal uMonetaryDcaTheory;

    @JsonProperty("uBondDcaTheory")
    private BigDecimal uBondDcaTheory;

    /** 六大类目标比例（canonical 名 → 百分数） */
    private Map<String, BigDecimal> targetRatios;

    /** 月度预算上限（user_config.monthly_budget_limit） */
    private BigDecimal budgetLimit;

    /** ZOH 补仓触发阈值（user_config.purchase_threshold） */
    private BigDecimal purchaseThreshold;

    /** 数据来源快照日期 */
    private String snapshotDate;

    /** 口径说明 */
    private String snapshotNote;

    /** 六大类金额明细（canonical 名 → 金额），便于前端直接展示 */
    private Map<String, BigDecimal> categories;
}
package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 2a `GET /api/correction/quarterly/defaults` 响应体。
 *
 * <p>季度 LQR-ZOH 联合校正操作台默认值：当前快照六大类 + user_config 参数。
 */
@Data
public class QuarterlyDefaultsResponse {

    /** 数据来源快照日期 */
    private String snapshotDate;

    /** 六大类合计（不含余额类） */
    @JsonProperty("vCurr")
    private BigDecimal vCurr;

    /** 高波合计市值（商品 + A股 + 海外 + 港股） */
    @JsonProperty("vHighVol")
    private BigDecimal vHighVol;

    /** 六大类金额明细（canonical 名 → 金额） */
    private Map<String, BigDecimal> categories;

    /** 六大类目标比例（canonical 名 → 百分数） */
    private Map<String, BigDecimal> targetRatios;

    /** 当月高波定投默认值（user_config.high_vol_dca_budget） */
    @JsonProperty("uHighDefault")
    private BigDecimal uHighDefault;

    /** ZOH 补仓触发阈值（user_config.purchase_threshold） */
    private BigDecimal purchaseThreshold;

    /** α 初始值建议（默认 0.20） */
    private BigDecimal alphaInitDefault;

    /** α 递减因子建议（默认 0.8） */
    private BigDecimal alphaDecayDefault;

    /** 低波定投份额理论反推值（U_high × p/(1-p_m-p_b)） */
    @JsonProperty("uMonetaryDcaTheory")
    private BigDecimal uMonetaryDcaTheory;

    @JsonProperty("uBondDcaTheory")
    private BigDecimal uBondDcaTheory;

    /** 口径说明 */
    private String snapshotNote;
}
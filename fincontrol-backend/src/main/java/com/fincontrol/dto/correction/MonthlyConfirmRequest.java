package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 2a `POST /api/correction/monthly/confirm` 请求体（[api-contract.md §5.4](#)）。
 *
 * <p>写入 {@code operation_log}（不修改 asset_raw / asset_snapshot）。
 * <p>2a 扩展字段（uMonetaryDca/uBondDca/totalInvestment/warnings/origin）可选。
 */
@Data
public class MonthlyConfirmRequest {

    private Long userId;

    @JsonProperty("vCurr")
    private BigDecimal vCurr;

    @JsonProperty("vMonetary")
    private BigDecimal vMonetary;

    @JsonProperty("vBond")
    private BigDecimal vBond;

    @JsonProperty("vHighVol")
    private BigDecimal vHighVol;

    @JsonProperty("uHigh")
    private BigDecimal uHigh;

    private BigDecimal deltaMTheory;
    private BigDecimal deltaBTheory;
    private BigDecimal deltaMActual;
    private BigDecimal deltaBActual;

    /** 低波货币定投份额（2a 扩展） */
    @JsonProperty("uMonetaryDca")
    private BigDecimal uMonetaryDca;

    /** 低波固收定投份额（2a 扩展） */
    @JsonProperty("uBondDca")
    private BigDecimal uBondDca;

    private String roundingStrategy;

    /** 数据来源快照日期 */
    private String snapshotDate;

    /** 预算上限（2a 扩展） */
    private BigDecimal budgetLimitUsed;

    /** 总投入（2a 扩展） */
    private BigDecimal totalInvestment;

    /** 触发边界（2a 扩展，如 "1"） */
    private String triggeredBoundary;

    /** 备注（2a 扩展，写入 warnings JSON） */
    private List<String> notes;

    /** 校正模式（2a 扩展，默认 zoh_only） */
    private String correctionMode;

    /** 数据来源（默认 correction_page） */
    private String source;
}
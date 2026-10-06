package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 2a `POST /api/correction/quarterly/confirm` 请求体（LQR-ZOH 联合校正确认入库）。
 *
 * <p>一次提交写入 4 张表（事务）：operation_log + correction_iteration +
 * correction_asset_detail + correction_param。
 */
@Data
public class QuarterlyConfirmRequest {

    private Long userId;

    /** 数据来源快照日期（必填） */
    private String snapshotDate;

    // ---------- operation_log 主字段 ----------
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
    @JsonProperty("uMonetaryDca")
    private BigDecimal uMonetaryDca;
    @JsonProperty("uBondDca")
    private BigDecimal uBondDca;
    private BigDecimal deltaMTheory;
    private BigDecimal deltaBTheory;
    private BigDecimal deltaMActual;
    private BigDecimal deltaBActual;
    private String roundingStrategy;
    private BigDecimal budgetLimitUsed;
    private BigDecimal totalInvestment;
    private String triggeredBoundary;
    private List<String> notes;

    /** 校正模式（默认 lqr_zoh） */
    private String correctionMode;

    /** 数据来源（默认 correction_page） */
    private String source;

    // ---------- 明细子表 ----------
    /** α 迭代/探针轮次 */
    private List<IterationItem> iterations;

    /** 逐资产明细（校正前/后 × 六大类与高波内部） */
    private List<AssetItem> assets;

    /** 参数与指标（key-value） */
    private List<ParamItem> params;

    @Data
    public static class IterationItem {
        private Integer sortOrder;
        private BigDecimal alpha;
        @JsonProperty("eHigh")
        private BigDecimal eHigh;
        private BigDecimal deltaM;
        private BigDecimal deltaB;
        private Boolean zohTriggered;
        private BigDecimal totalInvestment;
        private BigDecimal overLimit;
        private String note;
    }

    @Data
    public static class AssetItem {
        /** pre_six / post_six / pre_high / post_high */
        private String phase;
        private String category;
        private BigDecimal amount;
        private BigDecimal ratioActual;
        private BigDecimal ratioTarget;
        private BigDecimal deviation;
        private BigDecimal deltaRaw;
        private BigDecimal deltaAmount;
        private String note;
    }

    @Data
    public static class ParamItem {
        private String key;
        private BigDecimal numValue;
        private String textValue;
    }
}
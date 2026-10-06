package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 2a `POST /api/correction/quarterly/calculate` 响应体（LQR-ZOH 联合校正完整过程）。
 *
 * <p>与静态站 §3.6 / 9-30 计算过程文档的记录结构同构：
 * 校正前状态 → 高波内部归一化 → 预算参数 → α 迭代/探针 → 最终方案 → IC-DRR → 执行方案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarterlyCalculateResponse {

    // ---------- 校准前状态 ----------
    @JsonProperty("vCurr")
    private BigDecimal vCurr;
    @JsonProperty("vHighVol")
    private BigDecimal vHighVol;
    /** 校正前六大类（占六大类口径） */
    private List<AssetRow> preSix;
    /** 高波内部归一化（内部占比口径） */
    private List<AssetRow> highVolPre;
    /** 锚定资产（超配最严重，Δ=0） */
    private String anchor;
    /** 目标比例（canonical 名 → 百分数） */
    private Map<String, BigDecimal> targetRatios;

    // ---------- 预算参数 ----------
    private Params params;

    // ---------- 求解过程 ----------
    /** α 探针/迭代表（每行一轮） */
    private List<AlphaRow> alphaTable;
    /** ZOH 阶跃点分析（Δm = 阈值时的 E_high 与总投入跃迁），不适用时为 null */
    private ZohStep zohStep;
    /** 最终选择方案 */
    private Chosen chosen;
    /** auto 模式下的 α 迭代次数 */
    private Integer alphaIterations;

    // ---------- 高波 LQR ----------
    private LqrSolve lqr;

    // ---------- IC-DRR ----------
    private IcDrr icDrr;

    // ---------- 校正后高波内部 ----------
    private List<AssetRow> highVolPost;

    // ---------- 执行方案 ----------
    private List<PlanItem> plan;

    private List<MonthlyCalculateResponse.WarningItem> warnings;

    // ===================== 嵌套结构 =====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetRow {
        private String category;
        private BigDecimal amount;
        /** 实际占比（%）：six 口径=占六大类；high 口径=高波内部 */
        private BigDecimal ratioActual;
        /** 目标占比（%） */
        private BigDecimal ratioTarget;
        /** 偏差（百分点） */
        private BigDecimal deviation;
        /** 本类补仓求解原值（元） */
        private BigDecimal deltaRaw;
        /** 本类补仓执行值（取整后，元） */
        private BigDecimal deltaAmount;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Params {
        private BigDecimal surplus;
        @JsonProperty("mMax")
        private BigDecimal mMax;
        /** default / manual */
        @JsonProperty("mMaxSource")
        private String mMaxSource;
        @JsonProperty("uHigh")
        private BigDecimal uHigh;
        @JsonProperty("uMonetaryDca")
        private BigDecimal uMonetaryDca;
        @JsonProperty("uBondDca")
        private BigDecimal uBondDca;
        private BigDecimal alphaInit;
        private BigDecimal alphaDecay;
        private BigDecimal purchaseThreshold;
        /** auto / mMaxCapped */
        private String mode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlphaRow {
        /** α（小数） */
        private BigDecimal alpha;
        @JsonProperty("eHigh")
        private BigDecimal eHigh;
        private BigDecimal deltaM;
        private BigDecimal deltaB;
        private Boolean zohTriggered;
        private BigDecimal totalInvestment;
        /** 超出 M_max 金额（元）；未超为 null */
        private BigDecimal overLimit;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZohStep {
        /** 触发点最小 E_high（元），Δm 恰好达到阈值 */
        @JsonProperty("eHighAtStep")
        private BigDecimal eHighAtStep;
        /** 触发点 Δm（元） */
        private BigDecimal deltaMAtStep;
        /** 触发后总投入（元） */
        private BigDecimal totalAfterStep;
        /** 不触发时（略低于触发点）总投入（元） */
        private BigDecimal totalBeforeStep;
        /** 阶跃增量（元） */
        private BigDecimal jumpAmount;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chosen {
        private BigDecimal alphaFinal;
        @JsonProperty("eHigh")
        private BigDecimal eHigh;
        private BigDecimal deltaMRaw;
        private BigDecimal deltaBRaw;
        /** 取整后实际补仓（未触发时为其 0） */
        private BigDecimal deltaMActual;
        private BigDecimal deltaBActual;
        private Boolean zohTriggered;
        private BigDecimal totalInvestment;
        /** round_up_10 / round_up_1 / none */
        private String roundingStrategy;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LqrSolve {
        /** 各高波资产补仓（含锚定资产 Δ=0） */
        private List<AssetRow> deltas;
        /** KKT 求解说明（内点解是否成立、边界解等） */
        private String kktNote;
        /** 触发 Beta 极端超配检查的资产 */
        private List<String> betaTriggered;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IcDrr {
        /** 校正前高波内部偏差平方和（百分点²） */
        @JsonProperty("fBefore")
        private BigDecimal fBefore;
        /** 校正后高波内部偏差平方和（百分点²） */
        @JsonProperty("fAfter")
        private BigDecimal fAfter;
        /** IC-DRR = (fBefore - fAfter) / fBefore × 100% */
        private BigDecimal ratioPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanItem {
        private String item;
        private BigDecimal amount;
        private String note;
    }
}
package com.fincontrol.dto.nav;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 2b `GET /api/nav/statistics` 响应体：绩效统计（与个人统计报告 Excel 的维度对齐）。
 *
 * <p>首版覆盖三组：核心绩效（指标总览 + 最大回撤 + 净值分档）、风险调整指标、分布与区间。
 * 全部由 {@code nav_history} 实时计算，不落冗余表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavStatisticsResponse {

    /** 数据范围（首/末净值日） */
    private String from;
    private String to;

    /** 样本天数（nav_history 行数，自然日连续记录） */
    private int totalDays;
    /** 有波动天数（日收益率 ≠ 0，剔除周末/节假日） */
    private int nonZeroDays;
    /** 零收益天数（周末 + 节假日） */
    private int zeroDays;
    /** 其中周末天数 */
    private int weekendDays;

    /**
     * 指标总览（键：dailyReturnPct=日收益率（非零样本）/ actualProfit / cumulativeProfit / nav / totalAsset）。
     */
    private Map<String, SeriesMetrics> metrics;

    private Drawdown drawdown;
    private Risk risk;

    /** 收益率区间分布（bins 步长 0.1%） */
    private List<HistBin> returnHistogram;
    /** 日收益金额区间分布（bins 步长 10 元） */
    private List<PnlBin> pnlHistogram;

    private Cumulative cumulative;

    // ===================== 嵌套结构 =====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeriesMetrics {
        private int count;
        private double mean;
        private double median;
        private double std;
        private double min;
        private double max;
        private double p1;
        private double p5;
        private double p10;
        private double p25;
        private double p50;
        private double p75;
        private double p90;
        private double p95;
        private double p99;
        /** 偏度（Excel SKEW 口径，样本修正） */
        private double skewness;
        /** 峰度（超额峰度，Excel KURT 口径；正态 = 0） */
        private double kurtosis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Drawdown {
        /** 最大回撤（%，负值） */
        private double maxDrawdownPct;
        private String peakDate;
        private double peakNav;
        private String troughDate;
        private double troughNav;
        /** 回撤持续天数（峰值日 → 谷底日） */
        private int durationDays;
        private int daysNavAbove100;
        private int daysNavAbove105;
        private int daysNavAbove110;
        private int daysNavAbove115;
        private int daysNavBelow100;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Risk {
        /** 年化收益 CAGR（%） */
        private double cagrPct;
        /** 年化波动（%，日标准差 × √252） */
        private double annualVolPct;
        private double riskFreePct;
        /** 夏普 = (CAGR − rf) / 年化波动 */
        private double sharpe;
        /** 卡玛 = CAGR / |最大回撤| */
        private double calmar;
        /** 索提诺 = (CAGR − rf) / 年化下行波动 */
        private double sortino;
        /** 年化下行波动（%） */
        private double annualDownsidePct;
        private double winRatePct;
        private double avgWinPct;
        private double avgLossPct;
        private double profitLossRatio;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistBin {
        private String bin;
        private int count;
        private double pct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PnlBin {
        private String bin;
        private int count;
        private double pct;
        private double total;
        private double avg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cumulative {
        private List<CumInterval> intervals;
        private double startValue;
        private double currentValue;
        private double peakValue;
        private String peakDate;
        private double valleyValue;
        private String valleyDate;
        /** 峰 → 谷回撤（元） */
        private double peakToValleyDrop;
        /** 谷 → 最新反弹（元） */
        private double valleyToNowRebound;
        private int belowZeroDays;
        private double belowZeroPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CumInterval {
        private int index;
        private String start;
        private String end;
        private int days;
        private double valley;
        private String valleyDate;
        /** 占样本天数比例（%） */
        private double pctOfTotal;
    }
}
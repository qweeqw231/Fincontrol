package com.fincontrol.service;

import com.fincontrol.dto.nav.NavStatisticsResponse;
import com.fincontrol.dto.nav.NavStatisticsResponse.CumInterval;
import com.fincontrol.dto.nav.NavStatisticsResponse.Cumulative;
import com.fincontrol.dto.nav.NavStatisticsResponse.Drawdown;
import com.fincontrol.dto.nav.NavStatisticsResponse.HistBin;
import com.fincontrol.dto.nav.NavStatisticsResponse.PnlBin;
import com.fincontrol.dto.nav.NavStatisticsResponse.Risk;
import com.fincontrol.dto.nav.NavStatisticsResponse.SeriesMetrics;
import com.fincontrol.entity.NavHistory;
import com.fincontrol.mapper.NavHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2b 绩效统计服务：由 {@code nav_history} 实时计算（与个人统计报告 Excel 维度对齐）。
 *
 * <ul>
 *   <li>核心绩效：指标总览（均值/P 分位/偏度/峰度）+ 最大回撤 + 净值分档天数</li>
 *   <li>风险调整：CAGR / 年化波动 / 夏普 / 卡玛 / 索提诺 / 胜率 / 盈亏比</li>
 *   <li>分布与区间：收益率直方图、日收益金额直方图、累加&lt;0 区间分析</li>
 * </ul>
 *
 * <p>口径说明：日收益率取"有波动天数"（剔除周末/节假日的 0% 记录）计算波动与胜率；
 * 净值 CAGR 用 (navEnd/navStart)^(365/自然日跨度) − 1；无风险利率固定 1.16%（与统计报告一致）。
 */
@Service
public class NavStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(NavStatisticsService.class);

    private static final double RISK_FREE = 0.0116;      // 1.16%
    private static final int TRADING_DAYS_PER_YEAR = 252;

    /** 收益率直方图：-5.5% ~ +3.0%，步长 0.1% */
    private static final double RET_BIN_START = -5.5;
    private static final double RET_BIN_END = 3.0;
    private static final double RET_BIN_STEP = 0.1;

    /** 日收益金额直方图：-160 ~ +130 元，步长 10 元 */
    private static final double PNL_BIN_START = -160;
    private static final double PNL_BIN_END = 130;
    private static final double PNL_BIN_STEP = 10;

    private final NavHistoryMapper navHistoryMapper;

    public NavStatisticsService(NavHistoryMapper navHistoryMapper) {
        this.navHistoryMapper = navHistoryMapper;
    }

    public NavStatisticsResponse compute(Long userId) {
        List<NavHistory> rows = navHistoryMapper.selectAllByUser(userId);
        // 仅保留有净值的行（Excel 尾部空行已在同步侧过滤，这里再兜底）
        List<NavHistory> data = new ArrayList<>();
        for (NavHistory r : rows) {
            if (r.getNavDate() != null && r.getNav() != null) {
                data.add(r);
            }
        }
        if (data.isEmpty()) {
            return NavStatisticsResponse.builder()
                    .from(null).to(null).totalDays(0).nonZeroDays(0).zeroDays(0).weekendDays(0)
                    .metrics(new LinkedHashMap<>())
                    .returnHistogram(List.of()).pnlHistogram(List.of())
                    .build();
        }

        int n = data.size();
        List<Double> nav = new ArrayList<>(n);
        List<Double> cum = new ArrayList<>(n);
        List<Double> pnl = new ArrayList<>(n);
        List<Double> ret = new ArrayList<>(n);       // 小数（0.0001 = 0.01%）
        List<Double> total = new ArrayList<>(n);
        int weekendDays = 0;
        for (NavHistory r : data) {
            nav.add(d(r.getNav()));
            cum.add(d(r.getCumulativeProfit()));
            pnl.add(d(r.getActualProfit()));
            ret.add(d(r.getDailyReturnPct()));
            total.add(d(r.getTotalAsset()));
            String wd = r.getWeekday();
            if ("周六".equals(wd) || "周日".equals(wd)) weekendDays++;
        }

        // 有波动样本（剔除 0% 日子）——波动率/胜率口径
        List<Double> retNZ = new ArrayList<>();
        for (double v : ret) {
            if (v != 0.0) retNZ.add(v);
        }
        int nonZeroDays = retNZ.size();
        int zeroDays = n - nonZeroDays;

        LocalDate from = data.get(0).getNavDate();
        LocalDate to = data.get(n - 1).getNavDate();

        // ---------- 指标总览 ----------
        Map<String, SeriesMetrics> metrics = new LinkedHashMap<>();
        metrics.put("dailyReturnPct", metricsOf(retNZ, true));
        metrics.put("actualProfit", metricsOf(pnl, false));
        metrics.put("cumulativeProfit", metricsOf(cum, false));
        metrics.put("nav", metricsOf(nav, false));
        metrics.put("totalAsset", metricsOf(total, false));

        // ---------- 最大回撤 / 净值分档 ----------
        Drawdown drawdown = drawdownOf(data, nav);

        // ---------- 风险调整指标 ----------
        Risk risk = riskOf(data, nav, retNZ, from, to, drawdown);

        // ---------- 分布 ----------
        List<HistBin> returnHistogram = returnHistogramOf(ret, n);
        List<PnlBin> pnlHistogram = pnlHistogramOf(pnl, n);

        // ---------- 累加分析 ----------
        Cumulative cumulative = cumulativeOf(data, cum, n);

        log.info("2b 绩效统计: userId={} days={} nonZero={} mdd={}%", userId, n, nonZeroDays,
                round2(drawdown.getMaxDrawdownPct()));

        return NavStatisticsResponse.builder()
                .from(from.toString()).to(to.toString())
                .totalDays(n).nonZeroDays(nonZeroDays).zeroDays(zeroDays).weekendDays(weekendDays)
                .metrics(metrics)
                .drawdown(drawdown)
                .risk(risk)
                .returnHistogram(returnHistogram)
                .pnlHistogram(pnlHistogram)
                .cumulative(cumulative)
                .build();
    }

    // ===================== 指标总览 =====================

    private SeriesMetrics metricsOf(List<Double> values, boolean percent) {
        if (values.isEmpty()) {
            return SeriesMetrics.builder().count(0).build();
        }
        double[] a = sorted(values);
        int n = a.length;
        double mean = mean(a);
        double std = sampleStd(a, mean);
        return SeriesMetrics.builder()
                .count(n)
                .mean(mean)
                .median(percentile(a, 50))
                .std(std)
                .min(a[0])
                .max(a[n - 1])
                .p1(percentile(a, 1))
                .p5(percentile(a, 5))
                .p10(percentile(a, 10))
                .p25(percentile(a, 25))
                .p50(percentile(a, 50))
                .p75(percentile(a, 75))
                .p90(percentile(a, 90))
                .p95(percentile(a, 95))
                .p99(percentile(a, 99))
                .skewness(n > 2 ? skewness(a, mean, std) : 0)
                .kurtosis(n > 3 ? kurtosis(a, mean, std) : 0)
                .build();
    }

    // ===================== 回撤 =====================

    private Drawdown drawdownOf(List<NavHistory> data, List<Double> nav) {
        double peak = nav.get(0);
        LocalDate peakDate = data.get(0).getNavDate();
        double maxDd = 0;
        LocalDate ddPeakDate = peakDate;
        LocalDate ddTroughDate = peakDate;
        double ddPeakNav = peak;
        double ddTroughNav = peak;
        int above100 = 0, above105 = 0, above110 = 0, above115 = 0, below100 = 0;
        for (int i = 0; i < nav.size(); i++) {
            double v = nav.get(i);
            if (v >= 1.00) above100++;
            if (v >= 1.05) above105++;
            if (v >= 1.10) above110++;
            if (v >= 1.15) above115++;
            if (v < 1.00) below100++;
            if (v > peak) {
                peak = v;
                peakDate = data.get(i).getNavDate();
            }
            double dd = (peak - v) / peak;
            if (dd > maxDd) {
                maxDd = dd;
                ddPeakDate = peakDate;
                ddTroughDate = data.get(i).getNavDate();
                ddPeakNav = peak;
                ddTroughNav = v;
            }
        }
        return Drawdown.builder()
                .maxDrawdownPct(-maxDd * 100)
                .peakDate(ddPeakDate.toString())
                .peakNav(ddPeakNav)
                .troughDate(ddTroughDate.toString())
                .troughNav(ddTroughNav)
                .durationDays((int) ChronoUnit.DAYS.between(ddPeakDate, ddTroughDate))
                .daysNavAbove100(above100)
                .daysNavAbove105(above105)
                .daysNavAbove110(above110)
                .daysNavAbove115(above115)
                .daysNavBelow100(below100)
                .build();
    }

    // ===================== 风险调整 =====================

    private Risk riskOf(List<NavHistory> data, List<Double> nav, List<Double> retNonZero,
                        LocalDate from, LocalDate to, Drawdown dd) {
        double navStart = nav.get(0);
        double navEnd = nav.get(nav.size() - 1);
        long spanDays = ChronoUnit.DAYS.between(from, to);

        double cagr = spanDays > 0 && navStart > 0
                ? Math.pow(navEnd / navStart, 365.0 / spanDays) - 1 : 0;
        double[] r = sorted(retNonZero);
        double meanR = mean(r);
        double stdR = sampleStd(r, meanR);
        double annualVol = stdR * Math.sqrt(TRADING_DAYS_PER_YEAR);

        // 下行半方差（仅非零样本的负值）
        double sumSq = 0;
        int count = 0;
        int win = 0;
        double sumWin = 0;
        int winCnt = 0;
        double sumLoss = 0;
        int lossCnt = 0;
        for (double v : retNonZero) {
            if (v < 0) {
                sumSq += v * v;
                sumLoss += v;
                lossCnt++;
            } else {
                win++;
                sumWin += v;
                winCnt++;
            }
            count++;
        }
        double downsideDev = count > 0 ? Math.sqrt(sumSq / count) : 0;
        double annualDownside = downsideDev * Math.sqrt(TRADING_DAYS_PER_YEAR);
        double avgWin = winCnt > 0 ? sumWin / winCnt : 0;
        double avgLoss = lossCnt > 0 ? sumLoss / lossCnt : 0;
        double mdd = Math.abs(dd.getMaxDrawdownPct()) / 100.0;

        return Risk.builder()
                .cagrPct(cagr * 100)
                .annualVolPct(annualVol * 100)
                .riskFreePct(RISK_FREE * 100)
                .sharpe(annualVol > 0 ? (cagr - RISK_FREE) / annualVol : 0)
                .calmar(mdd > 0 ? cagr / mdd : 0)
                .sortino(annualDownside > 0 ? (cagr - RISK_FREE) / annualDownside : 0)
                .annualDownsidePct(annualDownside * 100)
                .winRatePct(count > 0 ? win * 100.0 / count : 0)
                .avgWinPct(avgWin * 100)
                .avgLossPct(avgLoss * 100)
                .profitLossRatio(avgLoss != 0 ? avgWin / Math.abs(avgLoss) : 0)
                .note("波动/胜率口径剔除 " + (data.size() - count) + " 个 0% 收益日（周末+节假日）"
                        + "；CAGR 按自然日跨度 " + ChronoUnit.DAYS.between(from, to) + " 天计算")
                .build();
    }

    // ===================== 分布 =====================

    private List<HistBin> returnHistogramOf(List<Double> ret, int total) {
        int bins = (int) Math.round((RET_BIN_END - RET_BIN_START) / RET_BIN_STEP); // 85
        int[] counts = new int[bins];
        int under = 0;
        int over = 0;
        for (double v : ret) {
            double pct = v * 100;
            if (pct < RET_BIN_START) {
                under++;
                continue;
            }
            if (pct >= RET_BIN_END) {
                over++;
                continue;
            }
            int idx = (int) Math.floor((pct - RET_BIN_START) / RET_BIN_STEP);
            if (idx >= bins) idx = bins - 1;
            counts[idx]++;
        }
        List<HistBin> out = new ArrayList<>(bins + 2);
        if (under > 0) {
            out.add(bin("< " + RET_BIN_START + "%", under, total));
        }
        for (int i = 0; i < bins; i++) {
            double lo = RET_BIN_START + i * RET_BIN_STEP;
            double hi = lo + RET_BIN_STEP;
            out.add(bin(fmt1(lo) + "% ~ " + fmt1(hi) + "%", counts[i], total));
        }
        if (over > 0) {
            out.add(bin("≥ " + RET_BIN_END + "%", over, total));
        }
        return out;
    }

    private List<PnlBin> pnlHistogramOf(List<Double> pnl, int total) {
        int bins = (int) Math.round((PNL_BIN_END - PNL_BIN_START) / PNL_BIN_STEP); // 29
        int[] counts = new int[bins];
        double[] sums = new double[bins];
        int zeroCount = 0;
        int under = 0;
        int over = 0;
        for (double v : pnl) {
            if (v == 0) {
                zeroCount++;
                continue;
            }
            if (v < PNL_BIN_START) {
                under++;
                continue;
            }
            if (v >= PNL_BIN_END) {
                over++;
                continue;
            }
            int idx = (int) Math.floor((v - PNL_BIN_START) / PNL_BIN_STEP);
            if (idx >= bins) idx = bins - 1;
            counts[idx]++;
            sums[idx] += v;
        }
        List<PnlBin> out = new ArrayList<>(bins + 3);
        out.add(pnlBin("{0}", zeroCount, 0, total));
        if (under > 0) {
            out.add(pnlBin("(-∞, " + (int) PNL_BIN_START + ")", under, 0, total));
        }
        for (int i = 0; i < bins; i++) {
            double lo = PNL_BIN_START + i * PNL_BIN_STEP;
            double hi = lo + PNL_BIN_STEP;
            out.add(pnlBin("[" + (int) lo + ", " + (int) hi + ")", counts[i], sums[i], total));
        }
        if (over > 0) {
            out.add(pnlBin("[" + (int) PNL_BIN_END + ", +∞)", over, 0, total));
        }
        return out;
    }

    private HistBin bin(String label, int count, int total) {
        return HistBin.builder().bin(label).count(count)
                .pct(total > 0 ? round2(count * 100.0 / total) : 0).build();
    }

    private PnlBin pnlBin(String label, int count, double sum, int total) {
        return PnlBin.builder().bin(label).count(count)
                .pct(total > 0 ? round2(count * 100.0 / total) : 0)
                .total(round2(sum))
                .avg(count > 0 ? round2(sum / count) : 0)
                .build();
    }

    // ===================== 累加<0 区间 =====================

    private Cumulative cumulativeOf(List<NavHistory> data, List<Double> cum, int total) {
        List<CumInterval> intervals = new ArrayList<>();
        int belowDays = 0;
        int i = 0;
        int idx = 0;
        while (i < cum.size()) {
            if (cum.get(i) < 0) {
                int start = i;
                double valley = cum.get(i);
                int valleyIdx = i;
                int j = i;
                while (j < cum.size() && cum.get(j) < 0) {
                    if (cum.get(j) < valley) {
                        valley = cum.get(j);
                        valleyIdx = j;
                    }
                    j++;
                }
                int days = j - start;
                belowDays += days;
                intervals.add(CumInterval.builder()
                        .index(++idx)
                        .start(data.get(start).getNavDate().toString())
                        .end(data.get(j - 1).getNavDate().toString())
                        .days(days)
                        .valley(round2(valley))
                        .valleyDate(data.get(valleyIdx).getNavDate().toString())
                        .pctOfTotal(round2(days * 100.0 / total))
                        .build());
                i = j;
            } else {
                i++;
            }
        }

        double startValue = cum.get(0);
        double current = cum.get(cum.size() - 1);
        double peak = cum.get(0);
        String peakDate = data.get(0).getNavDate().toString();
        double valley = cum.get(0);
        String valleyDate = data.get(0).getNavDate().toString();
        for (int k = 0; k < cum.size(); k++) {
            double v = cum.get(k);
            if (v > peak) {
                peak = v;
                peakDate = data.get(k).getNavDate().toString();
            }
            if (v < valley) {
                valley = v;
                valleyDate = data.get(k).getNavDate().toString();
            }
        }
        return Cumulative.builder()
                .intervals(intervals)
                .startValue(round2(startValue))
                .currentValue(round2(current))
                .peakValue(round2(peak))
                .peakDate(peakDate)
                .valleyValue(round2(valley))
                .valleyDate(valleyDate)
                .peakToValleyDrop(round2(peak - valley))
                .valleyToNowRebound(round2(current - valley))
                .belowZeroDays(belowDays)
                .belowZeroPct(total > 0 ? round2(belowDays * 100.0 / total) : 0)
                .build();
    }

    // ===================== 统计工具 =====================

    private static double[] sorted(List<Double> values) {
        double[] a = new double[values.size()];
        for (int i = 0; i < a.length; i++) a[i] = values.get(i);
        java.util.Arrays.sort(a);
        return a;
    }

    private static double mean(double[] a) {
        if (a.length == 0) return 0;
        double s = 0;
        for (double v : a) s += v;
        return s / a.length;
    }

    /** 样本标准差（n−1，Excel STDEV.S 口径） */
    private static double sampleStd(double[] a, double mean) {
        if (a.length < 2) return 0;
        double s = 0;
        for (double v : a) s += (v - mean) * (v - mean);
        return Math.sqrt(s / (a.length - 1));
    }

    /** 线性插值分位数（numpy 口径） */
    private static double percentile(double[] sorted, double q) {
        if (sorted.length == 0) return 0;
        if (sorted.length == 1) return sorted[0];
        double pos = q / 100.0 * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sorted[lo];
        double frac = pos - lo;
        return sorted[lo] + frac * (sorted[hi] - sorted[lo]);
    }

    /** 偏度（Excel SKEW 样本修正口径） */
    private static double skewness(double[] a, double mean, double std) {
        int n = a.length;
        if (n < 3 || std == 0) return 0;
        double s = 0;
        for (double v : a) s += Math.pow((v - mean) / std, 3);
        return (double) n / ((n - 1.0) * (n - 2.0)) * s;
    }

    /** 超额峰度（Excel KURT 口径；正态 = 0） */
    private static double kurtosis(double[] a, double mean, double std) {
        int n = a.length;
        if (n < 4 || std == 0) return 0;
        double s = 0;
        for (double v : a) s += Math.pow((v - mean) / std, 4);
        return (double) n * (n + 1) / ((n - 1.0) * (n - 2.0) * (n - 3.0)) * s
                - 3.0 * (n - 1.0) * (n - 1.0) / ((n - 2.0) * (n - 3.0));
    }

    private static double d(java.math.BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String fmt1(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
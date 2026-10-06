package com.fincontrol.service;

import com.fincontrol.dto.nav.NavStatisticsResponse;
import com.fincontrol.entity.NavHistory;
import com.fincontrol.mapper.NavHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 2b：{@link NavStatisticsService} —— 用 10 天构造数据验证口径
 * （最大回撤 / 净值分档 / 风险调整 / 累加&lt;0 区间 / 直方图计数）。
 */
class NavStatisticsServiceTest {

    private static final Long USER_ID = 1L;

    private static final double[] NAV = {1.00, 1.10, 1.05, 1.20, 1.25, 1.30, 1.10, 1.15, 1.20, 1.30};
    private static final double[] CUM = {0.01, 100, -20, 150, 200, 250, 100, 150, 200, 300};
    private static final double[] PNL = {0, 99.99, -120, 170, 50, 50, -150, 50, 50, 100};
    private static final double[] RET = {0, 0.1, -0.045, 0.143, 0.042, 0.04, -0.154, 0.045, 0.043, 0.083};

    @Test
    @DisplayName("2b-ST01 · 核心绩效：样本口径 / 最大回撤 / 净值分档")
    void coreMetrics() {
        NavStatisticsResponse r = compute();

        assertThat(r.getTotalDays()).isEqualTo(10);
        assertThat(r.getNonZeroDays()).isEqualTo(9);
        assertThat(r.getZeroDays()).isEqualTo(1);
        assertThat(r.getWeekendDays()).isEqualTo(3);
        assertThat(r.getFrom()).isEqualTo("2026-01-01");
        assertThat(r.getTo()).isEqualTo("2026-01-10");

        // 日收益率口径剔除 0 值样本
        assertThat(r.getMetrics().get("dailyReturnPct").getCount()).isEqualTo(9);
        assertThat(r.getMetrics().get("nav").getCount()).isEqualTo(10);
        assertThat(r.getMetrics().get("cumulativeProfit").getCount()).isEqualTo(10);

        // 最大回撤：峰值 1.30（1/6）→ 谷底 1.10（1/7）
        assertClose(r.getDrawdown().getMaxDrawdownPct(), -15.38, 0.01);
        assertThat(r.getDrawdown().getPeakDate()).isEqualTo("2026-01-06");
        assertThat(r.getDrawdown().getTroughDate()).isEqualTo("2026-01-07");
        assertThat(r.getDrawdown().getDurationDays()).isEqualTo(1);

        assertThat(r.getDrawdown().getDaysNavAbove100()).isEqualTo(10);
        assertThat(r.getDrawdown().getDaysNavAbove110()).isEqualTo(8);
        assertThat(r.getDrawdown().getDaysNavBelow100()).isZero();
    }

    @Test
    @DisplayName("2b-ST02 · 风险调整：胜率 7/9、盈亏比 > 0、CAGR/波动同向")
    void riskMetrics() {
        NavStatisticsResponse r = compute();
        NavStatisticsResponse.Risk risk = r.getRisk();

        assertClose(risk.getWinRatePct(), 100.0 * 7 / 9, 0.01);
        assertThat(risk.getAvgWinPct()).isGreaterThan(0);
        assertThat(risk.getAvgLossPct()).isLessThan(0);
        assertThat(risk.getProfitLossRatio()).isGreaterThan(0);
        assertThat(risk.getCagrPct()).isGreaterThan(0);
        assertThat(risk.getAnnualVolPct()).isGreaterThan(0);
        assertThat(risk.getSharpe()).isGreaterThan(0);
        assertThat(risk.getCalmar()).isGreaterThan(0);
        assertThat(risk.getSortino()).isGreaterThan(0);
        assertThat(risk.getNote()).contains("剔除 1 个 0% 收益日");
    }

    @Test
    @DisplayName("2b-ST03 · 累加<0 区间：单日区间 + 极值/回撤/反弹")
    void cumulativeIntervals() {
        NavStatisticsResponse r = compute();
        NavStatisticsResponse.Cumulative c = r.getCumulative();

        assertThat(c.getIntervals()).hasSize(1);
        NavStatisticsResponse.CumInterval i1 = c.getIntervals().get(0);
        assertThat(i1.getIndex()).isEqualTo(1);
        assertThat(i1.getStart()).isEqualTo("2026-01-03");
        assertThat(i1.getEnd()).isEqualTo("2026-01-03");
        assertThat(i1.getDays()).isEqualTo(1);
        assertClose(i1.getValley(), -20, 0.001);
        assertClose(i1.getPctOfTotal(), 10.0, 0.01);

        assertThat(c.getBelowZeroDays()).isEqualTo(1);
        assertClose(c.getBelowZeroPct(), 10.0, 0.01);
        assertClose(c.getPeakValue(), 300, 0.001);
        assertThat(c.getPeakDate()).isEqualTo("2026-01-10");
        assertClose(c.getValleyValue(), -20, 0.001);
        assertThat(c.getValleyDate()).isEqualTo("2026-01-03");
        assertClose(c.getPeakToValleyDrop(), 320, 0.001);
        assertClose(c.getValleyToNowRebound(), 320, 0.001);
    }

    @Test
    @DisplayName("2b-ST04 · 分布直方图：计数总和 = 样本数；0 值进入专属 bin")
    void distributions() {
        NavStatisticsResponse r = compute();

        int retTotal = r.getReturnHistogram().stream().mapToInt(NavStatisticsResponse.HistBin::getCount).sum();
        assertThat(retTotal).isEqualTo(10);

        int pnlTotal = r.getPnlHistogram().stream().mapToInt(NavStatisticsResponse.PnlBin::getCount).sum();
        assertThat(pnlTotal).isEqualTo(10);
        NavStatisticsResponse.PnlBin zeroBin = r.getPnlHistogram().stream()
                .filter(b -> "{0}".equals(b.getBin())).findFirst().orElseThrow();
        assertThat(zeroBin.getCount()).isEqualTo(1);
        // -150 落在 [-160, -150)? → 实际落在 [-150, -140)（边界取 floor）
        NavStatisticsResponse.PnlBin negBin = r.getPnlHistogram().stream()
                .filter(b -> "[-150, -140)".equals(b.getBin())).findFirst().orElseThrow();
        assertThat(negBin.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("2b-ST05 · 空数据：返回全零结构不抛异常")
    void emptyData() {
        NavHistoryMapper mapper = mock(NavHistoryMapper.class);
        when(mapper.selectAllByUser(anyLong())).thenReturn(new ArrayList<>());
        NavStatisticsService svc = new NavStatisticsService(mapper);

        NavStatisticsResponse r = svc.compute(USER_ID);

        assertThat(r.getTotalDays()).isZero();
        assertThat(r.getMetrics()).isEmpty();
        assertThat(r.getReturnHistogram()).isEmpty();
        assertThat(r.getDrawdown()).isNull();
    }

    // ===================== helpers =====================

    private NavStatisticsResponse compute() {
        NavHistoryMapper mapper = mock(NavHistoryMapper.class);
        when(mapper.selectAllByUser(anyLong())).thenReturn(rows());
        return new NavStatisticsService(mapper).compute(USER_ID);
    }

    private List<NavHistory> rows() {
        String[] weekdays = {"周四", "周五", "周六", "周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        List<NavHistory> rows = new ArrayList<>();
        for (int i = 0; i < NAV.length; i++) {
            NavHistory h = new NavHistory();
            h.setUserId(USER_ID);
            h.setNavDate(LocalDate.of(2026, 1, 1).plusDays(i));
            h.setWeekday(weekdays[i]);
            h.setNav(bd(NAV[i]));
            h.setCumulativeProfit(bd(CUM[i]));
            h.setActualProfit(bd(PNL[i]));
            h.setDailyReturnPct(bd(RET[i]));
            h.setTotalAsset(bd(1000 + i * 100));
            rows.add(h);
        }
        return rows;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static void assertClose(double actual, double expected, double tolerance) {
        assertThat(Math.abs(actual - expected) <= tolerance)
                .as("|%s - %s| ≤ %s", actual, expected, tolerance)
                .isTrue();
    }
}
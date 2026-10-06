package com.fincontrol.it;

import com.fincontrol.AbstractIT;
import com.fincontrol.entity.NavHistory;
import com.fincontrol.mapper.NavHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2b：nav_history upsert 幂等性 H2 集成测试（校验 ON DUPLICATE KEY UPDATE 在 H2 MySQL 模式可用）。
 */
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NavSyncH2Test extends AbstractIT {

    private static final long TEST_USER_ID = 20020L;

    @Autowired
    private NavHistoryMapper navHistoryMapper;

    @Test
    @Transactional
    @DisplayName("2b-H01 · 同一日期重复 upsert → 单行更新不重复")
    void upsert_sameDate_updatesInPlace() {
        NavHistory row = nav("2026-10-05", "1.0951", "-18.01");
        navHistoryMapper.upsert(row);

        row.setNav(new BigDecimal("1.1000"));
        row.setCumulativeProfit(new BigDecimal("25.00"));
        navHistoryMapper.upsert(row);

        List<NavHistory> all = navHistoryMapper.selectAllByUser(TEST_USER_ID);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getNav()).isEqualByComparingTo("1.1000");
        assertThat(all.get(0).getCumulativeProfit()).isEqualByComparingTo("25.00");
    }

    @Test
    @Transactional
    @DisplayName("2b-H02 · 多日期 upsert 后按日期升序读取")
    void upsert_multipleDays_sortedAsc() {
        navHistoryMapper.upsert(nav("2026-10-05", "1.0951", "-18.01"));
        navHistoryMapper.upsert(nav("2026-10-03", "1.0900", "-20.00"));
        navHistoryMapper.upsert(nav("2026-10-04", "1.0920", "-19.00"));

        List<NavHistory> all = navHistoryMapper.selectAllByUser(TEST_USER_ID);
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getNavDate()).isEqualTo(LocalDate.parse("2026-10-03"));
        assertThat(all.get(2).getNavDate()).isEqualTo(LocalDate.parse("2026-10-05"));
    }

    private static NavHistory nav(String date, String nav, String cum) {
        NavHistory h = new NavHistory();
        h.setUserId(TEST_USER_ID);
        h.setNavDate(LocalDate.parse(date));
        h.setWeekday("周一");
        h.setDailyReturnPct(new BigDecimal("0.0010"));
        h.setActualProfit(new BigDecimal("1.50"));
        h.setCumulativeProfit(new BigDecimal(cum));
        h.setNav(new BigDecimal(nav));
        h.setNavPct(new BigDecimal("9.51"));
        h.setTotalAsset(new BigDecimal("9653.88"));
        return h;
    }
}
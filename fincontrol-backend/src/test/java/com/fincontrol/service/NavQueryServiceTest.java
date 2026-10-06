package com.fincontrol.service;

import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.NavHistoryMapper;
import com.fincontrol.mapper.NavMilestoneMapper;
import com.fincontrol.mapper.OperationLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 2026-10-07 修复回归：`GET /api/ratio/history` 比例塌 0 事故。
 *
 * <p>背景：9/29、10/6 两点 `asset_snapshot.actual_ratio` 为 0（confirm 流程在百分比缺失时写 0），
 * 旧实现直接读该冗余列导致比例曲线塌 0；新实现按同日期权威金额实时重算。
 */
@ExtendWith(MockitoExtension.class)
class NavQueryServiceTest {

    @Mock private NavHistoryMapper navHistoryMapper;
    @Mock private NavMilestoneMapper navMilestoneMapper;
    @Mock private AssetSnapshotMapper assetSnapshotMapper;
    @Mock private OperationLogMapper operationLogMapper;

    @InjectMocks private NavQueryService navQueryService;

    @Test
    @DisplayName("ratio/history：actual_ratio=0 的日期按金额重算（10/6 真实数据），六大类合计 100%")
    @SuppressWarnings("unchecked")
    void ratioHistory_recomputesFromAmounts_whenStoredRatioZero() {
        when(assetSnapshotMapper.selectList(any())).thenReturn(List.of(
                snap("2026-10-06", "货币类", "988.36", "0.00"),
                snap("2026-10-06", "固收类", "1487.09", "0.00"),
                snap("2026-10-06", "商品类", "2430.57", "99.99"),
                snap("2026-10-06", "A股权益类", "2421.09", "0.00"),
                snap("2026-10-06", "海外权益类", "2140.36", "0.00"),
                snap("2026-10-06", "港股大中华类", "446.18", "0.00"),
                snap("2026-10-06", "余额类", "618.84", "0.00")
        ));

        Map<String, Object> res = navQueryService.getRatioHistory(1L);
        List<Map<String, Object>> points = (List<Map<String, Object>>) res.get("points");
        assertThat(points).hasSize(1);

        Map<String, Object> p = points.get(0);
        assertThat(p.get("date")).isEqualTo("2026-10-06");
        // 六大类 = amount / 六大类合计（9913.65）；存量脏值（商品 99.99）也被重算覆盖
        assertThat((BigDecimal) p.get("货币类")).isEqualByComparingTo("9.97");
        assertThat((BigDecimal) p.get("固收类")).isEqualByComparingTo("15.00");
        assertThat((BigDecimal) p.get("商品类")).isEqualByComparingTo("24.52");
        assertThat((BigDecimal) p.get("A股权益类")).isEqualByComparingTo("24.42");
        assertThat((BigDecimal) p.get("海外权益类")).isEqualByComparingTo("21.59");
        assertThat((BigDecimal) p.get("港股大中华类")).isEqualByComparingTo("4.50");
        // 余额类 = 余额 / 总资产（9913.65 + 618.84 = 10532.49）
        assertThat((BigDecimal) p.get("余额类")).isEqualByComparingTo("5.88");
        // 金额原样保留
        assertThat((BigDecimal) p.get("货币类_amount")).isEqualByComparingTo("988.36");

        BigDecimal sixSum = new BigDecimal("0");
        for (String cat : List.of("货币类", "固收类", "商品类", "A股权益类", "海外权益类", "港股大中华类")) {
            sixSum = sixSum.add((BigDecimal) p.get(cat));
        }
        assertThat(sixSum).isBetween(new BigDecimal("99.99"), new BigDecimal("100.01"));
    }

    @Test
    @DisplayName("ratio/history：多日期按日期升序返回")
    @SuppressWarnings("unchecked")
    void ratioHistory_sortsByDateAsc() {
        when(assetSnapshotMapper.selectList(any())).thenReturn(List.of(
                snap("2026-09-29", "货币类", "988.33", "0.00"),
                snap("2026-09-29", "固收类", "1096.46", "0.00"),
                snap("2026-10-06", "货币类", "988.36", "0.00"),
                snap("2026-10-06", "固收类", "1487.09", "0.00")
        ));

        Map<String, Object> res = navQueryService.getRatioHistory(1L);
        List<Map<String, Object>> points = (List<Map<String, Object>>) res.get("points");
        assertThat(points).hasSize(2);
        assertThat(points.get(0).get("date")).isEqualTo("2026-09-29");
        assertThat(points.get(1).get("date")).isEqualTo("2026-10-06");
        // 9/29：货币 988.33 / (988.33+1096.46) = 47.41%
        assertThat((BigDecimal) points.get(0).get("货币类")).isEqualByComparingTo("47.41");
    }

    private static AssetSnapshot snap(String date, String category, String amount, String storedRatio) {
        AssetSnapshot s = new AssetSnapshot();
        s.setUserId(1L);
        s.setSnapshotDate(LocalDate.parse(date));
        s.setCategory(category);
        s.setTotalAmount(new BigDecimal(amount));
        s.setActualRatio(new BigDecimal(storedRatio));
        s.setIsLatest(true);
        return s;
    }
}
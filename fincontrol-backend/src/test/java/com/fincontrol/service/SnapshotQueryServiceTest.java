package com.fincontrol.service;

import com.fincontrol.dto.snapshot.SnapshotByDateResponse;
import com.fincontrol.dto.snapshot.SnapshotCategorySummary;
import com.fincontrol.dto.snapshot.SnapshotHistoryResponse;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetRawQueryMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.SnapshotMetaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.4 + 1b.3 补救 SnapshotQueryService 业务测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnapshotQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SNAP_DATE = LocalDate.of(2026, 7, 16);

    @Mock private AssetSnapshotMapper assetSnapshotMapper;
    @Mock private AssetRawMapper assetRawMapper;
    @Mock private AssetRawQueryMapper assetRawQueryMapper;
    @Mock private SnapshotMetaMapper snapshotMetaMapper;
    @Mock private UserConfigService userConfigService;
    @Mock private CurrentSnapshotContext currentSnapshotContext;

    @InjectMocks private SnapshotQueryService service;

    private AssetSnapshot snapOf(String category, BigDecimal total, BigDecimal target, BigDecimal actual) {
        AssetSnapshot snap = new AssetSnapshot();
        snap.setUserId(USER_ID);
        snap.setSnapshotDate(SNAP_DATE);
        snap.setCategory(category);
        snap.setTotalAmount(total);
        snap.setTargetRatio(target);
        snap.setActualRatio(actual);
        snap.setIsLatest(true);
        snap.setCreatedAt(LocalDateTime.of(2026, 7, 16, 10, 0));
        snap.setUpdatedAt(LocalDateTime.of(2026, 7, 16, 10, 0));
        return snap;
    }

    private AssetRaw rawOf(String fund, String category, BigDecimal amount) {
        AssetRaw row = new AssetRaw();
        row.setUserId(USER_ID);
        row.setSnapshotDate(SNAP_DATE);
        row.setFundName(fund);
        row.setCategory(category);
        row.setAmount(amount);
        row.setIsLatest(true);
        return row;
    }

    @BeforeEach
    void setUp() {
        when(currentSnapshotContext.resolveCurrentDate(USER_ID)).thenReturn(SNAP_DATE);
        when(userConfigService.loadSixCategoryTargetRatios(USER_ID)).thenReturn(Map.of(
                "货币类", new BigDecimal("10"),
                "固收类", new BigDecimal("15"),
                "商品类", new BigDecimal("25"),
                "A股权益类", new BigDecimal("25"),
                "海外权益类", new BigDecimal("20"),
                "港股大中华类", new BigDecimal("5")));
    }

    @Test
    @DisplayName("R3: 无 currentDate → 返回 null")
    void latest_noCurrentDate() {
        when(currentSnapshotContext.resolveCurrentDate(USER_ID)).thenReturn(null);
        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, true);
        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("R3/R4: snapshot 列表存在 → recompute sixTotal + target ratios")
    void latest_ratiosAreRecomputed() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("796.34"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("固收类", new BigDecimal("890.36"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("商品类", new BigDecimal("1642.86"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("A股权益类", new BigDecimal("2149.98"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("海外权益类", new BigDecimal("1675.98"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("港股大中华类", new BigDecimal("386.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("余额类", new BigDecimal("308.86"), BigDecimal.ZERO, BigDecimal.ZERO)));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(2);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, true);

        assertThat(resp).isNotNull();
        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("7541.52");
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("308.86");
        assertThat(resp.getTotalAssetWithBalance()).isEqualByComparingTo("7850.38");
        SnapshotCategorySummary commodity = resp.getCategories().stream()
                .filter(c -> "商品类".equals(c.getCategoryName())).findFirst().orElseThrow();
        assertThat(commodity.getActualRatio()).isEqualByComparingTo("21.78");
        assertThat(commodity.getTargetRatio()).isEqualByComparingTo("25");
        assertThat(commodity.getDeviation()).isEqualByComparingTo("-3.22");
        SnapshotCategorySummary balance = resp.getCategories().stream()
                .filter(c -> "余额类".equals(c.getCategoryName())).findFirst().orElseThrow();
        assertThat(balance.getActualRatio()).isNull();
        assertThat(balance.getTargetRatio()).isNull();
    }

    @Test
    @DisplayName("R4: 当前 snapshot 历史 0 比例时，权威 raw 重算")
    void latest_recomputesFromRawWhenSnapshotRatioZero() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("796.34"), BigDecimal.ZERO, BigDecimal.ZERO)));
        // sumSixCategoryAmountsAtDate 返回权威金额
        when(assetRawQueryMapper.sumSixCategoryAmountsAtDate(USER_ID, SNAP_DATE))
                .thenReturn(List.of(Map.of("category", "货币类", "total_amount", new BigDecimal("796.34"))));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(1);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, false);
        assertThat(resp).isNotNull();
        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("796.34");
    }

    @Test
    @DisplayName("R3: includeBalance=false 时余额类不进入 categories")
    void latest_excludeBalance() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("796.34"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("余额类", new BigDecimal("308.86"), BigDecimal.ZERO, BigDecimal.ZERO)));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(1);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, false);
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("0");
        assertThat(resp.getCategories()).extracting("categoryName")
                .containsExactly("货币类");
    }
}

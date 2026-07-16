package com.fincontrol.service;

import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.4 Slice A：SnapshotQueryService 业务测试（A4-S01–S03）。
 */
@ExtendWith(MockitoExtension.class)
class SnapshotQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SNAP_DATE = LocalDate.of(2026, 7, 16);

    @Mock
    private AssetSnapshotMapper assetSnapshotMapper;

    @Mock
    private AssetRawMapper assetRawMapper;

    @InjectMocks
    private SnapshotQueryService service;

    private AssetSnapshot snapOf(String category, BigDecimal total,
                                 BigDecimal target, BigDecimal actual) {
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

    private AssetRaw rawOf(String fund, String category, BigDecimal amount, BigDecimal profit, boolean latest) {
        AssetRaw row = new AssetRaw();
        row.setUserId(USER_ID);
        row.setSnapshotDate(SNAP_DATE);
        row.setFundName(fund);
        row.setCategory(category);
        row.setAmount(amount);
        row.setProfit(profit);
        row.setSource("screenshot_manual");
        row.setIsLatest(latest);
        return row;
    }

    @BeforeEach
    void setUp() {
        // 默认 no-op；每个 case 自己打桩
    }

    @Test
    @DisplayName("A4-S01: 用户没有任何 snapshot 时返回 null，不抛异常")
    void latest_noSnapshot_returnsNull() {
        when(assetSnapshotMapper.selectLatestSnapshotDate(USER_ID)).thenReturn(null);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, true);

        assertThat(resp).isNull();
        verify(assetSnapshotMapper).selectLatestSnapshotDate(USER_ID);
        verify(assetSnapshotMapper, never()).selectLatestByUserAndDate(anyLong(), any());
        verify(assetRawMapper, never()).selectByUserAndDateAndCategory(anyLong(), any(), any());
    }

    @Test
    @DisplayName("A4-S02: latest 汇总多分类字段正确")
    void latest_summary_categories() {
        when(assetSnapshotMapper.selectLatestSnapshotDate(USER_ID)).thenReturn(SNAP_DATE);
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("641.49"), new BigDecimal("10"), new BigDecimal("9.90")),
                snapOf("固收类", new BigDecimal("954.24"), new BigDecimal("15"), new BigDecimal("14.72")),
                snapOf("余额类", new BigDecimal("418.46"), new BigDecimal("0"), new BigDecimal("0"))));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(0);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, true);

        assertThat(resp).isNotNull();
        assertThat(resp.getSnapshotDate()).isEqualTo(SNAP_DATE);
        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("1595.73");
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("418.46");
        assertThat(resp.getTotalAssetWithBalance()).isEqualByComparingTo("2014.19");
        assertThat(resp.getCategories()).hasSize(3);
        assertThat(resp.getCategories().stream().map(c -> c.getCategoryName()))
                .containsExactly("货币类", "固收类", "余额类");
    }

    @Test
    @DisplayName("A4-S02: includeBalance=false 时余额类不进入汇总和 totalAssetWithBalance")
    void latest_summary_excludeBalance() {
        when(assetSnapshotMapper.selectLatestSnapshotDate(USER_ID)).thenReturn(SNAP_DATE);
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("641.49"), new BigDecimal("10"), new BigDecimal("9.90")),
                snapOf("余额类", new BigDecimal("418.46"), new BigDecimal("0"), new BigDecimal("0"))));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(0);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, false);

        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("641.49");
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("0");
        assertThat(resp.getTotalAssetWithBalance()).isEqualByComparingTo("641.49");
        assertThat(resp.getCategories()).extracting("categoryName").containsExactly("货币类");
    }

    @Test
    @DisplayName("A4-S03: latest/detail 包含 funds 明细，profit 来自 raw")
    void latest_detail_includesFunds() {
        when(assetSnapshotMapper.selectLatestSnapshotDate(USER_ID)).thenReturn(SNAP_DATE);
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("641.49"), new BigDecimal("10"), new BigDecimal("9.90"))));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(USER_ID, SNAP_DATE, "货币类")).thenReturn(1);
        when(assetRawMapper.selectByUserAndDateAndCategory(USER_ID, SNAP_DATE, "货币类")).thenReturn(List.of(
                rawOf("中加货币E", "货币类", new BigDecimal("641.49"), new BigDecimal("1.49"), true),
                rawOf("OLD-IGNORE", "货币类", new BigDecimal("999.00"), new BigDecimal("0"), false)));

        SnapshotLatestResponse resp = service.getLatest(USER_ID, true, true);

        assertThat(resp.getCategories()).hasSize(1);
        var cat = resp.getCategories().get(0);
        assertThat(cat.getCategoryName()).isEqualTo("货币类");
        assertThat(cat.getFundCount()).isEqualTo(1);
        assertThat(cat.getFunds()).hasSize(1);
        var fund = cat.getFunds().get(0);
        assertThat(fund.getFundName()).isEqualTo("中加货币E");
        assertThat(fund.getProfit()).isEqualByComparingTo("1.49");
        assertThat(fund.getCategory()).isEqualTo("货币类");
    }

    @Test
    @DisplayName("A4-S01 变体: 找到日期但 snapshot 列表为空，返回 null")
    void latest_noRowsForDate_returnsNull() {
        when(assetSnapshotMapper.selectLatestSnapshotDate(USER_ID)).thenReturn(SNAP_DATE);
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE))
                .thenReturn(Collections.emptyList());

        SnapshotLatestResponse resp = service.getLatest(USER_ID, true, true);

        assertThat(resp).isNull();
        verify(assetRawMapper, never()).selectByUserAndDateAndCategory(anyLong(), any(), any());
    }
}

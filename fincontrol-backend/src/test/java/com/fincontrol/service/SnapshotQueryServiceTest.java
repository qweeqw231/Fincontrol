package com.fincontrol.service;

import com.fincontrol.dto.snapshot.SnapshotByDateResponse;
import com.fincontrol.dto.snapshot.SnapshotHistoryResponse;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.4 Slice A：SnapshotQueryService 业务测试（A4-S01–S03）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnapshotQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SNAP_DATE = LocalDate.of(2026, 7, 16);

    @Mock
    private AssetSnapshotMapper assetSnapshotMapper;

    @Mock
    private AssetRawMapper assetRawMapper;
    @Mock
    private SnapshotMetaMapper snapshotMetaMapper; // 1b.3.4

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
        // 1b.3.4 决策 27：change getLatest 用 is_current 查询
        // 默认 stub snapshotMetaMapper.selectCurrentDateByUser(USER_ID) = SNAP_DATE
        when(snapshotMetaMapper.selectCurrentDateByUser(USER_ID)).thenReturn(SNAP_DATE);
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

    // ========================================================================
    // 1a.4 Slice B：指定日期 + history
    // ========================================================================

    @Test
    @DisplayName("A4-S04: 指定日期找到 snapshot，返回分类汇总")
    void byDate_returnsCategorySummary() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("641.49"), new BigDecimal("10"), new BigDecimal("9.90")),
                snapOf("固收类", new BigDecimal("954.24"), new BigDecimal("15"), new BigDecimal("14.72")),
                snapOf("余额类", new BigDecimal("418.46"), new BigDecimal("0"), new BigDecimal("0"))));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(0);

        SnapshotByDateResponse resp = service.getByDate(USER_ID, SNAP_DATE, true);

        assertThat(resp.getSnapshotDate()).isEqualTo(SNAP_DATE);
        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("1595.73");
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("418.46");
        assertThat(resp.getCategories()).extracting("categoryName")
                .containsExactly("货币类", "固收类", "余额类");
    }

    @Test
    @DisplayName("A4-S04: 指定日期没数据，抛 2001 SNAPSHOT_NOT_FOUND")
    void byDate_notFound_throws2001() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE))
                .thenReturn(Collections.emptyList());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.getByDate(USER_ID, SNAP_DATE, true))
                .isInstanceOf(com.fincontrol.common.BusinessException.class)
                .extracting("errorCode").isEqualTo(com.fincontrol.common.ErrorCode.SNAPSHOT_NOT_FOUND);
    }

    @Test
    @DisplayName("A4-S05: history 列表按日期倒序、分页正确")
    void history_sortedAndPaged() {
        LocalDate d1 = LocalDate.of(2026, 7, 16);
        LocalDate d2 = LocalDate.of(2026, 7, 15);
        LocalDate d3 = LocalDate.of(2026, 7, 14);
        when(assetSnapshotMapper.selectHistoryDates(USER_ID, null, null))
                .thenReturn(List.of(d1, d2, d3));
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, d1))
                .thenReturn(List.of(snapOf("货币类", new BigDecimal("641.49"), new BigDecimal("10"), new BigDecimal("9.90"))));
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, d2))
                .thenReturn(List.of(snapOf("固收类", new BigDecimal("954.24"), new BigDecimal("15"), new BigDecimal("14.72"))));
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, d3))
                .thenReturn(List.of(snapOf("权益类", new BigDecimal("1000.00"), new BigDecimal("20"), new BigDecimal("20.00"))));

        SnapshotHistoryResponse page1 = service.getHistory(USER_ID, null, null, 1, 2, true);
        SnapshotHistoryResponse page2 = service.getHistory(USER_ID, null, null, 2, 2, true);

        assertThat(page1.getTotal()).isEqualTo(3);
        assertThat(page1.getPage()).isEqualTo(1);
        assertThat(page1.getPageSize()).isEqualTo(2);
        assertThat(page1.getItems()).extracting("snapshotDate").containsExactly(d1, d2);
        assertThat(page2.getItems()).extracting("snapshotDate").containsExactly(d3);
        assertThat(page1.getItems().get(0).getCategoryCount()).isEqualTo(1);
        assertThat(page1.getItems().get(0).getSixCategoriesTotal()).isEqualByComparingTo("641.49");
    }

    @Test
    @DisplayName("A4-S05: history 区间 + includeBalance=false 时余额类不计入 totalAmount")
    void history_rangeExcludesBalanceWhenDisabled() {
        LocalDate d1 = LocalDate.of(2026, 7, 16);
        when(assetSnapshotMapper.selectHistoryDates(USER_ID,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(d1));
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, d1)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("641.49"), new BigDecimal("10"), new BigDecimal("9.90")),
                snapOf("余额类", new BigDecimal("418.46"), new BigDecimal("0"), new BigDecimal("0"))));

        SnapshotHistoryResponse resp = service.getHistory(USER_ID,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 1, 20, false);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getItems().get(0).getBalanceFund()).isEqualByComparingTo("0");
        assertThat(resp.getItems().get(0).getCategoryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A4-S05: history pageSize 越界自动纠正到 1–100")
    void history_pageSizeClamp() {
        when(assetSnapshotMapper.selectHistoryDates(USER_ID, null, null))
                .thenReturn(Collections.emptyList());

        SnapshotHistoryResponse tooSmall = service.getHistory(USER_ID, null, null, 1, 0, true);
        SnapshotHistoryResponse tooBig = service.getHistory(USER_ID, null, null, 1, 9999, true);

        assertThat(tooSmall.getPageSize()).isEqualTo(20); // 默认值
        assertThat(tooBig.getPageSize()).isEqualTo(100);
    }
}

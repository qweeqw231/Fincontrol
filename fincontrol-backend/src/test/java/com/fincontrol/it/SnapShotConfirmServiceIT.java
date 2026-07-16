package com.fincontrol.it;

import com.fincontrol.AbstractIT;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.snapshot.SnapshotConfirmRequest;
import com.fincontrol.dto.snapshot.SnapshotConfirmResult;
import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.DedupEngine;
import com.fincontrol.service.SnapShotConfirmService;
import com.fincontrol.service.SnapshotRollbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.3 confirm + 1a.8 rollback 业务测试（[docs/phase-1/designs/1a3-dedup-strategy.md §7](#)）。
 *
 * <p>本类使用 @MockBean 替身 3 个 mapper（不依赖真实 SQL 解析）：
 * <ul>
 *   <li>AssetRawMapper、AssetSnapshotMapper、FundCategoryMapMapper 全部 mock</li>
 *   <li>DedupEngine 使用真实实现，warning/冲突场景用 spy 局部 stub</li>
 *   <li>业务逻辑（dedup 集成 + 镜像校验 + 10s 撤销）真实验证</li>
 * </ul>
 *
 * <p>真实 SQL 集成测试（asset_raw/asset_snapshot/fund_category_map 表实际 MERGE 行为）
 * 留到 1a.4 启动时用 MySQL 8.0 真库跑。
 */
class SnapShotConfirmServiceIT extends AbstractIT {

    @Autowired private SnapShotConfirmService confirmService;
    @Autowired private SnapshotRollbackService rollbackService;
    /**
     * 保留真实 dedup 实现；需要覆盖 warning/冲突场景时，用 spy 局部 stub。
     */
    @SpyBean private DedupEngine dedupEngine;

    @MockBean private AssetRawMapper assetRawMapper;
    @MockBean private AssetSnapshotMapper assetSnapshotMapper;
    @MockBean private FundCategoryMapMapper fundCategoryMapMapper;

    private static final Long USER_ID = 1L;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 16);

    // ========================================================================
    // 1a.3 confirm 业务测试
    // ========================================================================

    @Test
    @DisplayName("正常 2 张图 dedup → 写 3 表 → 镜像校验通过")
    void confirm_normal2Images_dedup3Tables_mirrorCheckPass() {
        SnapshotConfirmRequest req = buildRequest(parseAsset1(), parseAsset2());
        // 假设 mapper 都成功，并为真实镜像校验提供与三张基金记录一致的读模型。
        stubMirror(
                Set.of("天弘纳指A", "长城短债债券A", "鹏华纯债债券D"),
                Map.of(
                        "权益类", new BigDecimal("200.00"),
                        "固收类", new BigDecimal("300.00")));
        when(assetRawMapper.insert(any(AssetRaw.class))).thenReturn(1);
        when(assetSnapshotMapper.upsertByCategory(any(AssetSnapshot.class))).thenReturn(1);
        when(fundCategoryMapMapper.upsertByFundName(any())).thenReturn(1);

        SnapshotConfirmResult result = confirmService.confirm(req);

        // 写入统计：1 张图 1 只基金 + 1 张图 2 只基金 = 3 条 raw/map。
        assertThat(result.getAssetRawInserted()).isEqualTo(3);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(2); // 权益类 + 固收类
        // 警告：空
        assertThat(result.getWarnings()).isEmpty();
        // 撤销钩子
        assertThat(result.isRollbackAvailable()).isTrue();
        assertThat(result.getRollbackDeadline()).isNotNull();
        // verify 3 个 mapper 都调过
        verify(assetRawMapper, times(3)).insert(any(AssetRaw.class));
        verify(assetSnapshotMapper, times(2)).upsertByCategory(any(AssetSnapshot.class));
        verify(fundCategoryMapMapper, times(3)).upsertByFundName(any());
    }

    @Test
    @DisplayName("单图 dedup → 直接写库")
    void confirm_singleImage_dedupWrites3Tables() {
        SnapshotConfirmRequest req = buildRequest(parseAsset1());
        stubMirror(
                Set.of("天弘纳指A"),
                Map.of("权益类", new BigDecimal("200.00")));
        when(assetRawMapper.insert(any(AssetRaw.class))).thenReturn(1);
        when(assetSnapshotMapper.upsertByCategory(any(AssetSnapshot.class))).thenReturn(1);
        when(fundCategoryMapMapper.upsertByFundName(any())).thenReturn(1);

        SnapshotConfirmResult result = confirmService.confirm(req);

        assertThat(result.getAssetRawInserted()).isEqualTo(1);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("同日 snapshot 已存在 + 未 confirmedOverwrite → 返 warnings 不写库")
    void confirm_existingSameDayWithoutOverwrite_returnsWarnings() {
        // 通过 @SpyBean DedupEngine 模拟"已存在" 警告
        com.fincontrol.service.DedupEngine.DedupResult dedupResultWithWarn = mockDedupResultWithWarning();
        // warning 已经包含在 mockDedupResultWithWarning() 的 DedupReport 中；
        // 不要对真实 record 的 report().warnings() 使用 when(...) 链式 stub。
        org.mockito.Mockito.doReturn(dedupResultWithWarn).when(dedupEngine).deduplicate(any());

        SnapshotConfirmRequest req = buildRequest(parseFund("天弘纳指A", "权益类", "100.00", "权益类"));
        req.setConfirmedOverwrite(false);

        SnapshotConfirmResult result = confirmService.confirm(req);

        // 不写库
        assertThat(result.getAssetRawInserted()).isEqualTo(0);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(0);
        // verify mapper 都没调
        verify(assetRawMapper, never()).insert(any(AssetRaw.class));
        verify(assetSnapshotMapper, never()).upsertByCategory(any(AssetSnapshot.class));
        verify(fundCategoryMapMapper, never()).upsertByFundName(any());
    }

    @Test
    @DisplayName("同日 snapshot 已存在 + confirmedOverwrite=true → 写库覆盖")
    void confirm_existingSameDayWithOverwrite_writesAndReplaces() {
        com.fincontrol.service.DedupEngine.DedupResult dedupResult = mockDedupResult();
        org.mockito.Mockito.doReturn(dedupResult).when(dedupEngine).deduplicate(any());

        SnapshotConfirmRequest req = buildRequest(parseFund("天弘纳指A", "权益类", "100.00", "权益类"));
        stubMirror(
                Set.of("天弘纳指A"),
                Map.of("权益类", new BigDecimal("100.00")));
        when(assetRawMapper.insert(any(AssetRaw.class))).thenReturn(1);
        when(assetSnapshotMapper.upsertByCategory(any(AssetSnapshot.class))).thenReturn(1);
        when(fundCategoryMapMapper.upsertByFundName(any())).thenReturn(1);
        req.setConfirmedOverwrite(true);

        SnapshotConfirmResult result = confirmService.confirm(req);

        // 写库
        assertThat(result.getAssetRawInserted()).isEqualTo(1);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("同 fund_name 不同 category → 抛 INTERNAL_ERROR（维度 D 冲突）")
    void confirm_sameFundAcrossCategories_throws() {
        // DedupEngine.deduplicate 抛异常
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.INTERNAL_ERROR,
                "fund '天弘纳指A' 在 权益类 与 商品类 之间冲突"))
                .when(dedupEngine).deduplicate(any());

        SnapshotConfirmRequest req = buildRequest(
                parseFund("天弘纳指A", "权益类", "100.00"),
                parseFund("天弘纳指A", "商品类", "50.00"));

        assertThatThrownBy(() -> confirmService.confirm(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).contains("天弘纳指A").contains("权益类").contains("商品类");
                });
        // 事务回滚：mapper 都不调
        verify(assetRawMapper, never()).insert(any(AssetRaw.class));
        verify(assetSnapshotMapper, never()).upsertByCategory(any(AssetSnapshot.class));
    }

    // ========================================================================
    // 1a.8 rollback 业务测试
    // ========================================================================

    @Test
    @DisplayName("rollback 10s 内 → is_latest 翻转")
    void rollback_within10s_flipsIsLatest() {
        // setup：mock mapper 找 snapshot
        AssetSnapshot existing = new AssetSnapshot();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setSnapshotDate(TEST_DATE);
        existing.setCategory("权益类");
        existing.setTotalAmount(new BigDecimal("100.00"));
        existing.setIsLatest(true);
        existing.setCreatedAt(LocalDateTime.now());
        when(assetSnapshotMapper.selectById(1L)).thenReturn(existing);
        when(assetSnapshotMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(1);
        when(assetRawMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(1);

        SnapshotRollbackService.RollbackResult rb = rollbackService.rollback(1L, USER_ID);

        assertThat(rb.isRolledBack()).isTrue();
        assertThat(rb.getAssetRawUpdated()).isEqualTo(1);
        assertThat(rb.getAssetSnapshotUpdated()).isEqualTo(1);
    }

    // ========================================================================
    // fixture helpers
    // ========================================================================

    /**
     * 为 confirm 的三项镜像校验配置与本次 dedup 结果一致的查询结果。
     */
    private void stubMirror(Set<String> fundNames, Map<String, BigDecimal> categoryTotals) {
        when(assetRawMapper.selectFundNamesByUserAndDate(any(), any())).thenReturn(fundNames);
        when(fundCategoryMapMapper.selectFundNamesByUser(any()))
                .thenReturn(List.copyOf(fundNames));
        when(assetRawMapper.sumAmountByUserAndDateAndCategory(any(), any(), any()))
                .thenAnswer(invocation -> categoryTotals.get(invocation.getArgument(2, String.class)));
        when(assetSnapshotMapper.countLatestByUserAndDateAndCategory(any(), any(), any()))
                .thenReturn(1);
    }

    /**
     * 默认 DedupEngine fixture（无警告无冲突）。
     */
    private com.fincontrol.service.DedupEngine.DedupResult mockDedupResult() {
        ParsedAsset pa = new ParsedAsset();
        pa.setConversationId("dedup-test");
        pa.setSnapshotDate(TEST_DATE.toString());

        CategoryBlock cat = new CategoryBlock();
        cat.setCategoryName("权益类");
        cat.setCategoryTotal(new BigDecimal("100.00"));
        cat.setCategoryPercentage(new BigDecimal("100.00"));
        FundLine fund = new FundLine();
        fund.setFundName("天弘纳指A");
        fund.setAmount(new BigDecimal("100.00"));
        fund.setProfit(new BigDecimal("5.00"));
        cat.setFunds(List.of(fund));
        pa.setCategories(List.of(cat));
        pa.setMatchedFunds(List.of("天弘纳指A"));

        com.fincontrol.service.DedupEngine.DedupReport report =
                new com.fincontrol.service.DedupEngine.DedupReport(1, 1, 0, List.of());
        return new com.fincontrol.service.DedupEngine.DedupResult(pa, report);
    }

    private com.fincontrol.service.DedupEngine.DedupResult mockDedupResultWithWarning() {
        com.fincontrol.service.DedupEngine.DedupResult base = mockDedupResult();
        java.util.List<com.fincontrol.service.DedupEngine.DedupWarning> warns = List.of(
                new com.fincontrol.service.DedupEngine.DedupWarning(
                        "OVERWRITE_REQUIRED", "snapshot_date 2026-07-16 已存在", java.util.Map.of()));
        com.fincontrol.service.DedupEngine.DedupReport reportWithWarn =
                new com.fincontrol.service.DedupEngine.DedupReport(1, 1, 0, warns);
        return new com.fincontrol.service.DedupEngine.DedupResult(base.merged(), reportWithWarn);
    }

    /**
     * 构造单基金单类目 ParsedAsset。
     */
    private ParsedAsset parseFund(String fundName, String category, String amount) {
        return parseFund(fundName, category, amount, category);
    }

    private ParsedAsset parseFund(String fundName, String category, String amount, String ignored) {
        CategoryBlock cat = new CategoryBlock();
        cat.setCategoryName(category);
        FundLine fund = new FundLine();
        fund.setFundName(fundName);
        fund.setAmount(new BigDecimal(amount));
        fund.setProfit(new BigDecimal("5.00"));
        cat.setFunds(List.of(fund));

        ParsedAsset a = new ParsedAsset();
        a.setConversationId("conv-" + fundName);
        a.setSnapshotDate(TEST_DATE.toString());
        a.setMatchedFunds(List.of(fundName));
        a.setCategories(List.of(cat));
        return a;
    }

    private ParsedAsset parseAsset1() {
        return parseFund("天弘纳指A", "权益类", "200.00", "权益类");
    }

    private ParsedAsset parseAsset2() {
        CategoryBlock cat2 = new CategoryBlock();
        cat2.setCategoryName("固收类");
        FundLine fund2a = new FundLine();
        fund2a.setFundName("长城短债债券A");
        fund2a.setAmount(new BigDecimal("150.00"));
        fund2a.setProfit(new BigDecimal("3.00"));
        FundLine fund2b = new FundLine();
        fund2b.setFundName("鹏华纯债债券D");
        fund2b.setAmount(new BigDecimal("150.00"));
        fund2b.setProfit(new BigDecimal("2.00"));
        cat2.setFunds(List.of(fund2a, fund2b));

        ParsedAsset a2 = new ParsedAsset();
        a2.setConversationId("conv-img2");
        a2.setSnapshotDate(TEST_DATE.toString());
        a2.setMatchedFunds(List.of("长城短债债券A", "鹏华纯债债券D"));
        a2.setCategories(List.of(cat2));
        return a2;
    }

    private SnapshotConfirmRequest buildRequest(ParsedAsset... assets) {
        SnapshotConfirmRequest req = new SnapshotConfirmRequest();
        req.setUserId(USER_ID);
        req.setSnapshotDate(TEST_DATE);
        req.setConfirmedOverwrite(false);
        req.setIncludeBalance(true);
        req.setParsedAssets(List.of(assets));
        return req;
    }
}

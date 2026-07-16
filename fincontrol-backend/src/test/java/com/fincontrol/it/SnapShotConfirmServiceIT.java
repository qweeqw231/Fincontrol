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
import com.fincontrol.service.SnapShotConfirmService;
import com.fincontrol.service.SnapshotRollbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1a.3 confirm + 1a.8 rollback 集成测试（[docs/phase-1/designs/1a3-dedup-strategy.md §7](#)）。
 *
 * <p>用 H2 内存库（schema-h2.sql）真实写库 + DedupEngine 5 维 + 镜像校验。
 * <p>覆盖场景：confirm 5 个 + rollback 成功 1 个。
 * <p>rollback 超时（10s 过期）测试暂用反射改 ROLLBACK_WINDOW_SECONDS — TODO：1a.4 抽 Clock 后用 Clock.fixed() mock。
 */
class SnapShotConfirmServiceIT extends AbstractIT {

    @Autowired
    private SnapShotConfirmService confirmService;

    @Autowired
    private SnapshotRollbackService rollbackService;

    @Autowired
    private com.fincontrol.mapper.AssetRawMapper assetRawMapper;

    @Autowired
    private com.fincontrol.mapper.AssetSnapshotMapper assetSnapshotMapper;

    @Autowired
    private com.fincontrol.mapper.FundCategoryMapMapper fundCategoryMapMapper;

    private static final Long USER_ID = 1L;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 16);

    // ========================================================================
    // 1a.3 confirm 集成测试
    // ========================================================================

    @Test
    @DisplayName("正常 2 张图 dedup → 写 3 表 → 镜像校验通过")
    void confirm_normal2Images_dedup3Tables_mirrorCheckPass() {
        SnapshotConfirmRequest req = buildRequest(
                parseAsset1(),
                parseAsset2()
        );

        SnapshotConfirmResult result = confirmService.confirm(req);

        // 写入统计
        assertThat(result.getAssetRawInserted()).isEqualTo(5);  // 2+3
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(3); // 3 categories
        // 警告：空
        assertThat(result.getWarnings()).isEmpty();
        // 撤销钩子
        assertThat(result.isRollbackAvailable()).isTrue();
        assertThat(result.getRollbackDeadline()).isNotNull();
        // 3 张表实际行数
        assertThat(assetRawMapper.selectFundNamesByUserAndDate(USER_ID, TEST_DATE)).hasSize(5);
        // assetSnapshot 每 category 一行 is_latest=true
        assertThat(assetSnapshotMapper.countLatestByUserAndDateAndCategory(USER_ID, TEST_DATE, "权益类")).isEqualTo(1);
        assertThat(assetSnapshotMapper.countLatestByUserAndDateAndCategory(USER_ID, TEST_DATE, "固收类")).isEqualTo(1);
        assertThat(assetSnapshotMapper.countLatestByUserAndDateAndCategory(USER_ID, TEST_DATE, "余额类")).isEqualTo(1);
        // 6 大类总额
        AssetSnapshot equity = latestSnapshot("权益类");
        assertThat(equity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("500.00")); // 200+300
    }

    @Test
    @DisplayName("单图 dedup → 直接写库")
    void confirm_singleImage_dedupWrites3Tables() {
        SnapshotConfirmRequest req = buildRequest(parseAsset1());

        SnapshotConfirmResult result = confirmService.confirm(req);

        assertThat(result.getAssetRawInserted()).isEqualTo(2);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(2);
    }

    @Test
    @DisplayName("同日 snapshot 已存在 + 未 confirmedOverwrite → 返 warnings 不写库")
    void confirm_existingSameDayWithoutOverwrite_returnsWarnings() {
        // 第一次：写一批 fund1
        confirmService.confirm(buildRequest(parseFund("天弘纳指A", "权益类", "100.00", "权益类")));

        // 第二次：同 date 写另一批；不传 confirmedOverwrite
        SnapshotConfirmRequest req2 = buildRequest(parseFund("国泰黄金ETF", "商品类", "200.00", "商品类"));
        req2.setConfirmedOverwrite(false);

        SnapshotConfirmResult result = confirmService.confirm(req2);

        // 不写库（返回的写库数 = 0）
        assertThat(result.getAssetRawInserted()).isEqualTo(0);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(0);
        // 警告必含 OVERWRITE_REQUIRED
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings().toString()).contains("已存在");
    }

    @Test
    @DisplayName("同日 snapshot 已存在 + confirmedOverwrite=true → 写库覆盖")
    void confirm_existingSameDayWithOverwrite_writesAndReplaces() {
        // 第一次
        confirmService.confirm(buildRequest(parseFund("天弘纳指A", "权益类", "100.00", "权益类")));

        // 第二次 + confirmedOverwrite=true
        SnapshotConfirmRequest req2 = buildRequest(parseFund("国泰黄金ETF", "商品类", "200.00", "商品类"));
        req2.setConfirmedOverwrite(true);

        SnapshotConfirmResult result = confirmService.confirm(req2);

        assertThat(result.getAssetRawInserted()).isEqualTo(1);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(1);
        // fund1 仍存在（原确认是 is_latest=true，新 fund 覆盖后 is_latest=false）
        assertThat(assetRawMapper.selectFundNamesByUserAndDate(USER_ID, TEST_DATE)).contains("天弘纳指A", "国泰黄金ETF");
        // 1a.3 测试：fund1 的 is_latest 被覆盖 → false
        AssetRaw fund1Row = latestRaw("天弘纳指A");
        assertThat(fund1Row.getIsLatest()).isFalse();
        // fund2 的 is_latest → true
        AssetRaw fund2Row = latestRaw("国泰黄金ETF");
        assertThat(fund2Row.getIsLatest()).isTrue();
    }

    @Test
    @DisplayName("同 fund_name 不同 category → 抛 INTERNAL_ERROR（维度 D 冲突）")
    void confirm_sameFundAcrossCategories_throws() {
        // 同 fund_name 出现两次 category 不同
        ParsedAsset a1 = parseAssetWithFund("天弘纳指A", "权益类", "100.00");
        ParsedAsset a2 = parseAssetWithFund("天弘纳指A", "商品类", "50.00");
        SnapshotConfirmRequest req = buildRequest(a1, a2);

        assertThatThrownBy(() -> confirmService.confirm(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).contains("天弘纳指A").contains("权益类").contains("商品类");
                });
        // 事务回滚 — 验证不写库
        assertThat(assetRawMapper.selectFundNamesByUserAndDate(USER_ID, TEST_DATE)).isEmpty();
    }

    // ========================================================================
    // 1a.8 rollback 集成测试
    // ========================================================================

    @Test
    @DisplayName("rollback 10s 内 → is_latest 翻转 + 恢复前版")
    void rollback_within10s_flipsIsLatest() {
        // 写入一批
        confirmService.confirm(buildRequest(parseFund("天弘纳指A", "权益类", "100.00", "权益类")));

        // 查询 snapshot id
        AssetSnapshot first = latestSnapshot("权益类");
        assertThat(first.getIsLatest()).isTrue();

        // 立刻 rollback（< 10s）
        SnapshotRollbackService.RollbackResult rb = rollbackService.rollback(first.getId(), USER_ID);
        assertThat(rb.isRolledBack()).isTrue();
        assertThat(rb.getAssetRawUpdated()).isEqualTo(1);
        assertThat(rb.getAssetSnapshotUpdated()).isEqualTo(1);
        // 第一批 is_latest 变 false
        AssetRaw rawAfter = latestRaw("天弘纳指A");
        assertThat(rawAfter.getIsLatest()).isFalse();
        AssetSnapshot snapAfter = latestSnapshot("权益类");
        // 没有更早的 snapshot → snap.is_latest 也变 false（无前版可恢复）
        assertThat(snapAfter).isNull();
    }

    // ========================================================================
    // fixture helpers
    // ========================================================================

    /**
     * 构造单基金单类目 ParsedAsset。
     */
    private ParsedAsset parseFund(String fundName, String category, String amount, String expectedCategory) {
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

    /**
     * 通用 fixture：构造单图（1 个 fund、1 个 category）。
     */
    private ParsedAsset parseAsset1() {
        return parseFund("天弘纳指A", "权益类", "200.00", "权益类");
    }

    /**
     * 通用 fixture：构造第 2 张图（不同 category）。
     */
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

    /**
     * 自定义 fundName + category 构造 ParsedAsset（1 个 fund）。
     */
    private ParsedAsset parseAssetWithFund(String fundName, String category, String amount) {
        return parseFund(fundName, category, amount, category);
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

    /**
     * 查询最新一条指定 fund_name 的 asset_raw。
     */
    private AssetRaw latestRaw(String fundName) {
        return assetRawMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AssetRaw>()
                        .eq("user_id", USER_ID)
                        .eq("snapshot_date", TEST_DATE)
                        .eq("fund_name", fundName)
                        .last("LIMIT 1")
        ).get(0);
    }

    /**
     * 查询 user + date 下 category 的最新一条 asset_snapshot。
     */
    private AssetSnapshot latestSnapshot(String category) {
        List<AssetSnapshot> list = assetSnapshotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AssetSnapshot>()
                        .eq("user_id", USER_ID)
                        .eq("snapshot_date", TEST_DATE)
                        .eq("category", category)
                        .last("LIMIT 1")
        );
        return list.isEmpty() ? null : list.get(0);
    }
}

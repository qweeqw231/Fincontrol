package com.fincontrol.it;

import com.fincontrol.AbstractIT;
import com.fincontrol.dto.snapshot.SnapshotConfirmRequest;
import com.fincontrol.dto.snapshot.SnapshotConfirmResult;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture.Fixture;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.SnapShotConfirmService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** A8V3-S11：H2 真实 Mapper + real SnapShotConfirmService + real DedupEngine。 */
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnapShotConfirmRealFourPageH2Test extends AbstractIT {

    private static final long TEST_USER_ID = 18008L;

    @Autowired private SnapShotConfirmService confirmService;
    @Autowired private AssetRawMapper assetRawMapper;
    @Autowired private AssetSnapshotMapper assetSnapshotMapper;
    @Autowired private FundCategoryMapMapper fundCategoryMapMapper;

    @Test
    @Transactional
    @DisplayName("A8V3-S11 · 四页 fixture 经 confirm 20→19，真实 H2 三表镜像合计 7884.68")
    void confirm_realFourPageFixture_writesExactNineteenAndMirrorMatches() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();
        LocalDate testDate = LocalDate.now();

        SnapshotConfirmRequest request = new SnapshotConfirmRequest();
        request.setUserId(TEST_USER_ID);
        request.setSnapshotDate(testDate);
        request.setSnapshotNote("A8V3-S11 H2 transaction fixture");
        request.setParsedAssets(fixture.parsedAssets());
        request.setIsIgnored(false);
        request.setConfirmedOverwrite(false);
        request.setIncludeBalance(true);

        SnapshotConfirmResult result = confirmService.confirm(request);

        assertThat(result.getDedupReport().inputRecordCount()).isEqualTo(20);
        assertThat(result.getDedupReport().mergedRecordCount()).isEqualTo(19);
        assertThat(result.getDedupReport().droppedCount()).isEqualTo(1);
        assertThat(result.getDedupReport().warnings()).isEmpty();
        assertThat(result.getAssetRawInserted()).isEqualTo(19);
        assertThat(result.getAssetSnapshotUpserted()).isEqualTo(7);
        assertThat(result.isRollbackAvailable()).isTrue();

        Set<String> expectedNames = fixture.expectedUnique().stream()
                .map(line -> line.fundName())
                .collect(Collectors.toSet());
        assertThat(assetRawMapper.selectFundNamesByUserAndDate(TEST_USER_ID, testDate))
                .containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(fundCategoryMapMapper.selectFundNamesByUser(TEST_USER_ID))
                .containsExactlyInAnyOrderElementsOf(expectedNames);

        Map<String, BigDecimal> expectedCategoryTotals = new LinkedHashMap<>();
        fixture.expectedUnique().forEach(line -> expectedCategoryTotals.merge(
                line.categoryName(), line.amount(), BigDecimal::add));

        List<AssetSnapshot> snapshots = assetSnapshotMapper.selectLatestByUserAndDate(TEST_USER_ID, testDate);
        assertThat(snapshots).hasSize(7);
        Map<String, BigDecimal> actualCategoryTotals = snapshots.stream().collect(Collectors.toMap(
                AssetSnapshot::getCategory,
                AssetSnapshot::getTotalAmount,
                (left, right) -> right,
                LinkedHashMap::new));
        assertThat(actualCategoryTotals).containsOnlyKeys(expectedCategoryTotals.keySet());
        expectedCategoryTotals.forEach((category, expected) ->
                assertThat(actualCategoryTotals.get(category))
                        .as(category + " total")
                        .isEqualByComparingTo(expected));
        assertThat(actualCategoryTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(fixture.expectedTotalAsset());
    }

    @Test
    @Transactional
    @DisplayName("A10P2-S01 · 余额类 余额宝 confirm 后 holding IS NULL（修复§9.1）")
    void confirm_balanceCategory_holdingIsNull() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();
        LocalDate testDate = LocalDate.now();

        SnapshotConfirmRequest request = new SnapshotConfirmRequest();
        request.setUserId(TEST_USER_ID);
        request.setSnapshotDate(testDate);
        request.setSnapshotNote("A10P2-S01 balance holding=null regression");
        request.setParsedAssets(fixture.parsedAssets());
        request.setIsIgnored(false);
        request.setConfirmedOverwrite(false);
        request.setIncludeBalance(true);

        confirmService.confirm(request);

        // 验证 余额宝 holding_profit / profit 为 NULL，cumulative_profit 透传保留
        List<AssetRaw> balanceRows = assetRawMapper.selectBalanceByUser(TEST_USER_ID);
        assertThat(balanceRows)
                .as("is_latest=true 余额类行")
                .isNotEmpty();
        AssetRaw yuebao = balanceRows.stream()
                .filter(r -> "余额宝".equals(r.getFundName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("余额宝未写入"));
        assertThat(yuebao.getCategory()).isEqualTo("余额类");
        assertThat(yuebao.getAmount()).isEqualByComparingTo(new BigDecimal("320.85"));
        // 核心断言：余额类 holding + profit 必须 NULL（不能被默认 0 覆盖）
        assertThat(yuebao.getHoldingProfit())
                .as("余额宝 holding_profit IS NULL")
                .isNull();
        assertThat(yuebao.getProfit())
                .as("余额宝 profit 兼容列 IS NULL")
                .isNull();
        // cumulative 透传保留（fixture 中为 1.89）
        assertThat(yuebao.getCumulativeProfit())
                .as("余额宝 cumulative_profit 透传 1.89")
                .isEqualByComparingTo(new BigDecimal("1.89"));
        // 余额类 asset_snapshot.balance_fund 保持 320.85
        List<AssetSnapshot> balanceSnap = assetSnapshotMapper.selectLatestByUserAndDate(TEST_USER_ID, testDate)
                .stream()
                .filter(s -> "余额类".equals(s.getCategory()))
                .toList();
        assertThat(balanceSnap).hasSize(1);
        assertThat(balanceSnap.get(0).getBalanceFund())
                .as("余额类 balance_fund = 320.85")
                .isEqualByComparingTo(new BigDecimal("320.85"));

        // 反向验证：非余额类 holding/profit 仍同步为非零数字（避免误伤其他类别）
        List<AssetRaw> allRows = assetRawMapper.selectByUserAndDateAndCategory(
                TEST_USER_ID, testDate, "货币类");
        assertThat(allRows).isNotEmpty();
        for (AssetRaw row : allRows) {
            assertThat(row.getHoldingProfit())
                    .as("非余额类 %s holding_profit 非 null", row.getFundName())
                    .isNotNull();
            assertThat(row.getProfit())
                    .as("非余额类 %s profit 非 null", row.getFundName())
                    .isNotNull();
        }
    }
}

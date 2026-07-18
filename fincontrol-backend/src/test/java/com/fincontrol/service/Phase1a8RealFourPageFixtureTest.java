package com.fincontrol.service;

import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture.ExpectedFund;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture.Fixture;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture.Page;
import com.fincontrol.service.DedupEngine.DedupInput;
import com.fincontrol.service.DedupEngine.DedupResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A8V3.1 真实四页 ground truth 自校验与 real DedupEngine 20→19。1a.8.7 holding + cumulative 双字段。 */
class Phase1a8RealFourPageFixtureTest {

    @Test
    @DisplayName("A8V3.1-S04 · fixture 自检：6/3/5/6、20 完整行、19 唯一、7884.68 总额、holding/cumulative 双字段")
    void fixture_isInternallyConsistent() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();

        // 1a.8.8：fixture 升 v3.2（7 canonical 类别名 + isUserConfirmed 字段）
        assertThat(fixture.fixtureVersion()).isEqualTo("1a.8-v3.2");
        assertThat(fixture.pages()).extracting(Page::expectedCompleteCount)
                .containsExactly(6, 3, 5, 6);
        assertThat(fixture.pages()).extracting(Page::headerOnly)
                .extracting(List::size)
                .containsExactly(1, 0, 0, 1);

        int completeCount = fixture.parsedAssets().stream()
                .flatMap(asset -> asset.getCategories().stream())
                .mapToInt(category -> category.getFunds().size())
                .sum();
        assertThat(completeCount).isEqualTo(fixture.expectedCompleteInputCount()).isEqualTo(20);

        // 1a.8.7 双字段：非余额类必填 holding_profit + cumulative_profit
        //       余额类（包含 余额宝）：holding_profit=null（Alipay 不显示），
        //                            cumulative_profit 可能非 null（如 余额宝 1.89 真实）或 null（其他无累计）
        fixture.parsedAssets().forEach(asset -> {
            assertThat(asset.getCategories()).isNotEmpty();
            for (CategoryBlock cat : asset.getCategories()) {
                boolean isBalance = "余额类".equals(cat.getCategoryName());
                for (FundLine fund : cat.getFunds()) {
                    if (isBalance) {
                        assertThat(fund.getHoldingProfit())
                                .as("余额类 %s holding_profit 必为 null（Alipay 不显示）", fund.getFundName())
                                .isNull();
                    } else {
                        assertThat(fund.getHoldingProfit())
                                .as("非余额类 %s 缺少 holding_profit", fund.getFundName())
                                .isNotNull();
                        assertThat(fund.getCumulativeProfit())
                                .as("非余额类 %s 缺少 cumulative_profit", fund.getFundName())
                                .isNotNull();
                    }
                }
            }
        });

        Map<String, ExpectedFund> expected = expectedByName(fixture);
        assertThat(expected).hasSize(fixture.expectedUniqueCount()).hasSize(19);
        BigDecimal total = expected.values().stream()
                .map(ExpectedFund::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(fixture.expectedUniqueTotalAmount());
        assertThat(fixture.pages().get(1).parsedAsset().getTotalAsset())
                .isEqualByComparingTo(new BigDecimal("7884.68"));
    }

    @Test
    @DisplayName("A8V3.1-S05 · real DedupEngine 20→19：holding + cumulative 双字段都精确一致")
    void dedup_realFourPages_merges20To19WithExactHoldingAndCumulative() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                fixture.parsedAssets(), Set.of(), LocalDate.parse(fixture.snapshotDate()), false));

        assertThat(result.report().inputRecordCount()).isEqualTo(20);
        assertThat(result.report().mergedRecordCount()).isEqualTo(19);
        assertThat(result.report().droppedCount()).isEqualTo(1);
        assertThat(result.report().warnings()).isEmpty();
        assertThat(result.merged().getTotalAsset()).isEqualByComparingTo(new BigDecimal("7884.68"));
        assertThat(result.merged().getMatchedFunds()).hasSize(19);

        Map<String, MergedActual> actual = actualByName(result.merged());
        assertThat(actual).hasSize(19);
        expectedByName(fixture).forEach((name, expected) -> {
            MergedActual line = actual.get(name);
            assertThat(line).as("缺少标的 %s", name).isNotNull();
            assertThat(line.categoryName()).as("%s category", name).isEqualTo(expected.categoryName());
            assertThat(line.amount()).as("%s amount", name).isEqualByComparingTo(expected.amount());
            // 1a.8.8：holding/cumulative 允许 null（余额类 Alipay 不显示）→ 单独 isNull 判断 + isEqualByComparingTo
            if (expected.holdingProfit() == null) {
                assertThat(line.holdingProfit()).as("%s holding_profit 必为 null", name).isNull();
            } else {
                assertThat(line.holdingProfit()).as("%s holding_profit", name)
                        .isEqualByComparingTo(expected.holdingProfit());
            }
            if (expected.cumulativeProfit() == null) {
                assertThat(line.cumulativeProfit()).as("%s cumulative_profit 必为 null", name).isNull();
            } else {
                assertThat(line.cumulativeProfit()).as("%s cumulative_profit", name)
                        .isEqualByComparingTo(expected.cumulativeProfit());
            }
        });
    }

    @Test
    @DisplayName("A8V3.1-S05b · 标题行不覆盖完整记录（holding + cumulative 同时为 null）")
    void dedup_headerOnlyRows_neverOverwriteCompleteRows() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();
        List<ParsedAsset> withHeaders = new ArrayList<>(fixture.parsedAssets());
        withHeaders.add(0, incompleteAsset("header-before", "海外权益类",
                "天弘纳斯达克100指数(QDII)C", null, null));

        ParsedAsset trailingHeaders = new ParsedAsset();
        trailingHeaders.setConversationId("header-after");
        trailingHeaders.setSnapshotDate(fixture.snapshotDate());
        CategoryBlock category = new CategoryBlock();
        category.setCategoryName("港股/大中华类");
        FundLine fund1 = new FundLine();
        fund1.setFundName("华安香港精选股票(QDII)");
        // 不提供 holding/cumulative 模拟仅标题
        FundLine fund2 = new FundLine();
        fund2.setFundName("只有标题的未知基金");
        category.setFunds(List.of(fund1, fund2));
        trailingHeaders.setCategories(List.of(category));
        withHeaders.add(trailingHeaders);

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                withHeaders, Set.of(), LocalDate.parse(fixture.snapshotDate()), false));

        assertThat(result.report().mergedRecordCount()).isEqualTo(19);
        assertThat(result.report().droppedCount()).isEqualTo(4);
        assertThat(result.report().warnings())
                .hasSize(3)
                .allMatch(warning -> "DATA_INCOMPLETE".equals(warning.code()));
        assertThat(result.merged().getTotalAsset()).isEqualByComparingTo(new BigDecimal("7884.68"));

        Map<String, MergedActual> actual = actualByName(result.merged());
        assertThat(actual.get("天弘纳斯达克100指数(QDII)C").amount())
                .isEqualByComparingTo(new BigDecimal("308.82"));
        assertThat(actual.get("天弘纳斯达克100指数(QDII)C").cumulativeProfit())
                .isEqualByComparingTo(new BigDecimal("33.82"));
        assertThat(actual.get("华安香港精选股票(QDII)").amount())
                .isEqualByComparingTo(new BigDecimal("121.11"));
        assertThat(actual.get("华安香港精选股票(QDII)").cumulativeProfit())
                .isEqualByComparingTo(new BigDecimal("1.11"));
        assertThat(actual).doesNotContainKey("只有标题的未知基金");
    }

    private static ParsedAsset incompleteAsset(String conversationId, String categoryName,
                                              String fundName, BigDecimal holding, BigDecimal cumulative) {
        ParsedAsset asset = new ParsedAsset();
        asset.setConversationId(conversationId);
        asset.setSnapshotDate("2026-07-15");
        FundLine line = new FundLine();
        line.setFundName(fundName);
        line.setAmount(null);
        line.setHoldingProfit(holding);
        line.setCumulativeProfit(cumulative);
        CategoryBlock category = new CategoryBlock();
        category.setCategoryName(categoryName);
        category.setFunds(List.of(line));
        asset.setCategories(List.of(category));
        return asset;
    }

    private static Map<String, ExpectedFund> expectedByName(Fixture fixture) {
        Map<String, ExpectedFund> byName = new LinkedHashMap<>();
        fixture.expectedUnique().forEach(line -> {
            ExpectedFund previous = byName.put(line.fundName(), line);
            assertThat(previous).as("expectedUnique 不得重名: %s", line.fundName()).isNull();
        });
        return byName;
    }

    private static Map<String, MergedActual> actualByName(ParsedAsset asset) {
        Map<String, MergedActual> byName = new LinkedHashMap<>();
        for (CategoryBlock category : asset.getCategories()) {
            for (FundLine line : category.getFunds()) {
                // 1a.8.8：fund 字段允许 null（余额类 holding 不显示）
                // 保留 null 透传；测试断言 expected.holdingProfit=null 时 isEqualByComparingTo 也能匹配
                byName.put(line.getFundName(), new MergedActual(
                        category.getCategoryName(), line.getAmount(), line.getHoldingProfit(), line.getCumulativeProfit()));
            }
        }
        return byName;
    }

    private record MergedActual(String categoryName, BigDecimal amount,
                                BigDecimal holdingProfit, BigDecimal cumulativeProfit) {
    }
}

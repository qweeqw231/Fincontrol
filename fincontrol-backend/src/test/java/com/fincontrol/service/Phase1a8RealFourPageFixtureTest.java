package com.fincontrol.service;

import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture.ExpectedFund;
import com.fincontrol.fixture.Phase1a8RealFourPageFixture.Fixture;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A8V3-S04/S05：真实四页 ground truth 自校验与 real DedupEngine 20→19。 */
class Phase1a8RealFourPageFixtureTest {

    @Test
    @DisplayName("A8V3-S04 · 四页 fixture = 6/3/5/6、20 条完整行、19 唯一、7884.68")
    void fixture_isInternallyConsistent() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();

        assertThat(fixture.fixtureVersion()).isEqualTo("1a.8-v3");
        assertThat(fixture.pages()).extracting(page -> page.expectedCompleteCount())
                .containsExactly(6, 3, 5, 6);
        assertThat(fixture.pages()).extracting(page -> page.headerOnly().size())
                .containsExactly(1, 0, 0, 1);

        int completeCount = fixture.parsedAssets().stream()
                .flatMap(asset -> asset.getCategories().stream())
                .mapToInt(category -> category.getFunds().size())
                .sum();
        assertThat(completeCount).isEqualTo(fixture.expectedCompleteInputCount()).isEqualTo(20);

        Map<String, ExpectedFund> expected = expectedByName(fixture);
        assertThat(expected).hasSize(fixture.expectedUniqueCount()).hasSize(19);
        assertThat(expected.values().stream().map(ExpectedFund::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(fixture.expectedTotalAsset());
        assertThat(fixture.pages().get(1).parsedAsset().getTotalAsset())
                .isEqualByComparingTo(new BigDecimal("7884.68"));
    }

    @Test
    @DisplayName("A8V3-S05 · 20 条完整行经 real DedupEngine 合并为 19，逐项与 7884.68 全对")
    void dedup_realFourPages_merges20To19WithExactValues() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                fixture.parsedAssets(), Set.of(), LocalDate.parse(fixture.snapshotDate()), false));

        assertThat(result.report().inputRecordCount()).isEqualTo(20);
        assertThat(result.report().mergedRecordCount()).isEqualTo(19);
        assertThat(result.report().droppedCount()).isEqualTo(1);
        assertThat(result.report().warnings()).isEmpty();
        assertThat(result.merged().getTotalAsset()).isEqualByComparingTo(fixture.expectedTotalAsset());
        assertThat(result.merged().getMatchedFunds()).hasSize(19);

        Map<String, ActualFund> actual = actualByName(result.merged());
        assertThat(actual).hasSize(19);
        expectedByName(fixture).forEach((name, expected) -> {
            ActualFund line = actual.get(name);
            assertThat(line).as("缺少标的 %s", name).isNotNull();
            assertThat(line.categoryName()).as(name + " category").isEqualTo(expected.categoryName());
            assertThat(line.amount()).as(name + " amount").isEqualByComparingTo(expected.amount());
            assertThat(line.profit()).as(name + " profit").isEqualByComparingTo(expected.profit());
        });
    }

    @Test
    @DisplayName("A8V3-S05 · 仅标题行无论前后都不能覆盖完整记录，唯一不完整行被 warning 丢弃")
    void dedup_headerOnlyRows_neverOverwriteCompleteRows() {
        Fixture fixture = Phase1a8RealFourPageFixture.load();
        List<ParsedAsset> withHeaders = new ArrayList<>();
        withHeaders.add(incompleteAsset("header-before", "海外权益类",
                "天弘纳斯达克100指数(QDII)C"));
        withHeaders.addAll(fixture.parsedAssets());

        ParsedAsset trailingHeaders = new ParsedAsset();
        trailingHeaders.setConversationId("header-after");
        trailingHeaders.setSnapshotDate(fixture.snapshotDate());
        CategoryBlock category = new CategoryBlock();
        category.setCategoryName("港股/大中华类");
        category.setFunds(List.of(
                incompleteFund("华安香港精选股票(QDII)"),
                incompleteFund("只有标题的未知基金")));
        trailingHeaders.setCategories(List.of(category));
        withHeaders.add(trailingHeaders);

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                withHeaders, Set.of(), LocalDate.parse(fixture.snapshotDate()), false));

        assertThat(result.report().inputRecordCount()).isEqualTo(23);
        assertThat(result.report().mergedRecordCount()).isEqualTo(19);
        assertThat(result.report().droppedCount()).isEqualTo(4);
        assertThat(result.report().warnings())
                .hasSize(3)
                .allMatch(warning -> "DATA_INCOMPLETE".equals(warning.code()));
        assertThat(result.merged().getTotalAsset()).isEqualByComparingTo(fixture.expectedTotalAsset());

        Map<String, ActualFund> actual = actualByName(result.merged());
        assertThat(actual.get("天弘纳斯达克100指数(QDII)C").amount())
                .isEqualByComparingTo(new BigDecimal("308.82"));
        assertThat(actual.get("华安香港精选股票(QDII)").amount())
                .isEqualByComparingTo(new BigDecimal("121.11"));
        assertThat(actual).doesNotContainKey("只有标题的未知基金");
    }

    private static ParsedAsset incompleteAsset(String conversationId, String categoryName, String fundName) {
        ParsedAsset asset = new ParsedAsset();
        asset.setConversationId(conversationId);
        asset.setSnapshotDate("2026-07-15");
        CategoryBlock category = new CategoryBlock();
        category.setCategoryName(categoryName);
        category.setFunds(List.of(incompleteFund(fundName)));
        asset.setCategories(List.of(category));
        return asset;
    }

    private static FundLine incompleteFund(String name) {
        FundLine line = new FundLine();
        line.setFundName(name);
        return line;
    }

    private static Map<String, ExpectedFund> expectedByName(Fixture fixture) {
        Map<String, ExpectedFund> byName = new LinkedHashMap<>();
        fixture.expectedUnique().forEach(line -> {
            ExpectedFund previous = byName.put(line.fundName(), line);
            assertThat(previous).as("expectedUnique 不得重名: %s", line.fundName()).isNull();
        });
        return byName;
    }

    private static Map<String, ActualFund> actualByName(ParsedAsset asset) {
        Map<String, ActualFund> byName = new LinkedHashMap<>();
        for (CategoryBlock category : asset.getCategories()) {
            for (FundLine line : category.getFunds()) {
                byName.put(line.getFundName(), new ActualFund(
                        category.getCategoryName(), line.getAmount(), line.getProfit()));
            }
        }
        return byName;
    }

    private record ActualFund(String categoryName, BigDecimal amount, BigDecimal profit) {
    }
}

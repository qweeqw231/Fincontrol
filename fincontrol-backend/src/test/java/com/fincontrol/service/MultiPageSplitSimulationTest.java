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

/**
 * 2026-10-06：多图任意张数切分模拟（前端解除"必须 4 张"限制为 1~10 张的配套验证）。
 *
 * <p>真实截图张数不可穷举（1~10 张的任意组合），但"N 张截图"在数据层面等价于
 * "把同一批持仓行按截屏顺序切成 N 段"——相邻段常带重叠行（真实滚动截图的常态）。
 * 因此用真实四页 fixture（20 行 / 19 只唯一 / 合计 7884.68）程序化重切为 K 段，
 * 验证以下不变量：
 * <ol>
 *   <li>任意段数（含 K=1 单页、K=20 每段一行）零重叠切分 → 去重结果与 ground truth 一致；</li>
 *   <li>相邻段重叠 1 行 → 不产生重复、不破坏金额；</li>
 *   <li>某基金被截断成 0 金额占位（后续截图再出现，后入）→ 完整记录获胜 + ZERO_AMOUNT_SKIPPED
 *       （安信新价值 390.98→0.00 事故回归）；</li>
 *   <li>双轨 totalAsset：各段页首一致 → top；不一致 → visible_sum + TOP_INCONSISTENT。</li>
 * </ol>
 */
class MultiPageSplitSimulationTest {

    private static final int[] PAGE_COUNTS = {1, 2, 3, 5, 8, 10, 20};
    private static final BigDecimal FIXTURE_TOTAL = new BigDecimal("7884.68");

    // ========================================================================
    // 场景 A：零重叠切分（K 张截图，每行恰好出现一次）
    // ========================================================================

    @Test
    @DisplayName("任意段数零重叠切分（K=1/2/3/5/8/10/20）→ 19 只唯一 + 金额与 ground truth 一致")
    void splitWithoutOverlap_anyPageCount() {
        for (int k : PAGE_COUNTS) {
            Fixture fx = Phase1a8RealFourPageFixture.load();
            List<Row> rows = flatten(fx);
            DedupResult result = dedup(splitInto(rows, k, fx.snapshotDate()), fx);

            assertThat(result.report().inputRecordCount())
                    .as("K=%d 输入行数", k).isEqualTo(20);
            assertThat(result.report().mergedRecordCount())
                    .as("K=%d 去重后唯一基金数", k).isEqualTo(19);
            assertInvariants(result, fx, "K=" + k);
        }
    }

    // ========================================================================
    // 场景 B：相邻段重叠（真实滚动截图常态）
    // ========================================================================

    @Test
    @DisplayName("相邻段重叠 1 行切分（K=2/3/5/8）→ 去重后仍与 ground truth 一致")
    void splitWithOneRowOverlap_keepsInvariants() {
        for (int k : new int[]{2, 3, 5, 8}) {
            Fixture fx = Phase1a8RealFourPageFixture.load();
            List<Row> rows = flatten(fx);
            DedupResult result = dedup(splitWithOverlap(rows, k, 1, fx.snapshotDate()), fx);

            assertThat(result.report().inputRecordCount())
                    .as("K=%d 重叠输入的记录总数", k).isEqualTo(20 + (k - 1));
            assertThat(result.report().mergedRecordCount())
                    .as("K=%d 去重后唯一基金数", k).isEqualTo(19);
            assertInvariants(result, fx, "K=" + k);
        }
    }

    // ========================================================================
    // 场景 C：截断行（0 金额占位）注入 → 完整记录获胜（安信事故回归）
    // ========================================================================

    @Test
    @DisplayName("某基金被截断成 0 金额占位（后入）→ 完整记录获胜 + ZERO_AMOUNT_SKIPPED")
    void truncatedZeroAmountLaterCopy_doesNotOverwrite() {
        Fixture fx = Phase1a8RealFourPageFixture.load();
        List<Row> rows = flatten(fx);
        List<ParsedAsset> assets = splitInto(rows, 3, fx.snapshotDate());

        // 取第 1 段里第一只非余额类基金 X；在**第 3 段**追加它的 0 金额截断副本（模拟后续截图重现该行但明细被截断）
        CategoryBlock srcBlock = findFirstNonBalanceBlock(assets.get(0));
        FundLine src = findFirstNonBalanceFund(srcBlock);
        String victimName = src.getFundName();

        FundLine zeroCopy = copyOf(src);
        zeroCopy.setAmount(BigDecimal.ZERO);
        zeroCopy.setProfit(BigDecimal.ZERO);
        zeroCopy.setHoldingProfit(BigDecimal.ZERO);
        zeroCopy.setCumulativeProfit(BigDecimal.ZERO);
        findOrCreateCategory(assets.get(2), srcBlock.getCategoryName())
                .getFunds().add(zeroCopy);

        DedupResult result = dedup(assets, fx);

        assertInvariants(result, fx, "截断注入");
        assertThat(result.report().warnings())
                .as("fund=%s 的 0 金额后入副本应触发 ZERO_AMOUNT_SKIPPED", victimName)
                .anyMatch(w -> "ZERO_AMOUNT_SKIPPED".equals(w.code())
                        && victimName.equals(w.context().get("fundName")));
    }

    // ========================================================================
    // 场景 D：双轨 totalAsset（K=5 切分）
    // ========================================================================

    @Test
    @DisplayName("各段页首 total 一致 → top；不一致 → visible_sum + TOP_INCONSISTENT（K=5）")
    void dualTrackTotalAsset_k5() {
        Fixture fx = Phase1a8RealFourPageFixture.load();
        List<Row> rows = flatten(fx);

        List<ParsedAsset> consistent = splitInto(rows, 5, fx.snapshotDate());
        consistent.forEach(a -> a.setTotalAsset(FIXTURE_TOTAL));
        DedupResult r1 = dedup(consistent, fx);
        assertThat(r1.merged().getTotalAsset()).isEqualByComparingTo(FIXTURE_TOTAL);
        assertThat(r1.merged().getTotalAssetSource()).isEqualTo("top");
        assertThat(r1.report().warnings()).noneMatch(w -> "DISCREPANCY".equals(w.code()));

        List<ParsedAsset> inconsistent = splitInto(rows, 5, fx.snapshotDate());
        inconsistent.get(0).setTotalAsset(FIXTURE_TOTAL);
        inconsistent.get(1).setTotalAsset(new BigDecimal("2987.32"));
        DedupResult r2 = dedup(inconsistent, fx);
        assertThat(r2.merged().getTotalAssetSource()).isEqualTo("visible_sum");
        assertThat(r2.merged().getTotalAsset()).isEqualByComparingTo(FIXTURE_TOTAL);
        assertThat(r2.report().warnings())
                .anyMatch(w -> "TOP_INCONSISTENT".equals(w.code()))
                .noneMatch(w -> "DISCREPANCY".equals(w.code()));
    }

    // ========================================================================
    // helpers
    // ========================================================================

    /** 一行持仓（扁平化自 fixture，保持原始行序）。 */
    private record Row(String categoryName, FundLine line) {}

    /** 扁平化 fixture 全部持仓行（含 row 级 copy，与 fixture 对象隔离）。 */
    private static List<Row> flatten(Fixture fx) {
        List<Row> rows = new ArrayList<>();
        for (ParsedAsset asset : fx.parsedAssets()) {
            for (CategoryBlock cat : asset.getCategories()) {
                for (FundLine fund : cat.getFunds()) {
                    rows.add(new Row(cat.getCategoryName(), copyOf(fund)));
                }
            }
        }
        return rows;
    }

    /** 把 rows 按行序切成 k 段（零重叠）。 */
    private static List<ParsedAsset> splitInto(List<Row> rows, int k, String date) {
        List<ParsedAsset> assets = new ArrayList<>();
        int base = rows.size() / k;
        int rem = rows.size() % k;
        int start = 0;
        for (int i = 0; i < k; i++) {
            int size = base + (i < rem ? 1 : 0);
            assets.add(toAsset(rows.subList(start, start + size), date, i + 1));
            start += size;
        }
        return assets;
    }

    /** 切 k 段并让相邻段共享尾部 overlap 行（模拟真实滚动截图重叠）。 */
    private static List<ParsedAsset> splitWithOverlap(List<Row> rows, int k, int overlap, String date) {
        List<ParsedAsset> assets = new ArrayList<>();
        int base = rows.size() / k;
        int rem = rows.size() % k;
        int start = 0;
        for (int i = 0; i < k; i++) {
            int size = base + (i < rem ? 1 : 0);
            int end = Math.min(start + size + (i < k - 1 ? overlap : 0), rows.size());
            assets.add(toAsset(rows.subList(start, end), date, i + 1));
            start += size;
        }
        return assets;
    }

    /** 段内行 → 一个 ParsedAsset（按类别聚合；行级 copy 保证各段独立）。 */
    private static ParsedAsset toAsset(List<Row> segment, String date, int seq) {
        ParsedAsset a = new ParsedAsset();
        a.setConversationId("sim-img-" + seq);
        a.setSnapshotDate(date);
        Map<String, List<FundLine>> byCat = new LinkedHashMap<>();
        for (Row r : segment) {
            byCat.computeIfAbsent(r.categoryName(), x -> new ArrayList<>()).add(copyOf(r.line()));
        }
        List<CategoryBlock> cats = new ArrayList<>();
        for (Map.Entry<String, List<FundLine>> e : byCat.entrySet()) {
            CategoryBlock cb = new CategoryBlock();
            cb.setCategoryName(e.getKey());
            cb.setFunds(e.getValue());
            cats.add(cb);
        }
        a.setCategories(cats);
        return a;
    }

    private static FundLine copyOf(FundLine f) {
        FundLine c = new FundLine();
        c.setFundName(f.getFundName());
        c.setAmount(f.getAmount());
        c.setProfit(f.getProfit());
        c.setHoldingProfit(f.getHoldingProfit());
        c.setCumulativeProfit(f.getCumulativeProfit());
        c.setIsUserConfirmed(f.getIsUserConfirmed());
        c.setConfirmedAt(f.getConfirmedAt());
        return c;
    }

    private static CategoryBlock findFirstNonBalanceBlock(ParsedAsset asset) {
        for (CategoryBlock c : asset.getCategories()) {
            for (FundLine f : c.getFunds()) {
                if (f.getHoldingProfit() != null && f.getAmount() != null
                        && f.getAmount().signum() > 0) {
                    return c;
                }
            }
        }
        throw new AssertionError("段内没有非余额类基金，无法注入截断行");
    }

    private static FundLine findFirstNonBalanceFund(CategoryBlock block) {
        for (FundLine f : block.getFunds()) {
            if (f.getHoldingProfit() != null && f.getAmount() != null
                    && f.getAmount().signum() > 0) {
                return f;
            }
        }
        throw new AssertionError("类别块内没有非余额类基金");
    }

    private static CategoryBlock findOrCreateCategory(ParsedAsset asset, String categoryName) {
        for (CategoryBlock c : asset.getCategories()) {
            if (categoryName.equals(c.getCategoryName())) {
                return c;
            }
        }
        CategoryBlock c = new CategoryBlock();
        c.setCategoryName(categoryName);
        c.setFunds(new ArrayList<>());
        asset.getCategories().add(c);
        return c;
    }

    private static DedupResult dedup(List<ParsedAsset> assets, Fixture fx) {
        return new DedupEngine().deduplicate(new DedupInput(
                assets, Set.of(), Map.of(), LocalDate.parse(fx.snapshotDate()), false));
    }

    /** 不变量：19 只唯一、每只字段与 ground truth 一致、合计 = fixture 总额。 */
    private static void assertInvariants(DedupResult result, Fixture fx, String tag) {
        Map<String, ExpectedFund> expected = new LinkedHashMap<>();
        fx.expectedUnique().forEach(e -> expected.put(e.fundName(), e));

        int count = 0;
        for (CategoryBlock c : result.merged().getCategories()) {
            for (FundLine f : c.getFunds()) {
                ExpectedFund exp = expected.get(f.getFundName());
                assertThat(exp).as("%s: 出现 ground truth 之外的基金 %s", tag, f.getFundName()).isNotNull();
                assertThat(c.getCategoryName()).as("%s: %s 类别", tag, f.getFundName())
                        .isEqualTo(exp.categoryName());
                assertThat(f.getAmount()).as("%s: %s 金额", tag, f.getFundName())
                        .isEqualByComparingTo(exp.amount());
                if (exp.holdingProfit() != null) {
                    assertThat(f.getHoldingProfit()).as("%s: %s 持有收益", tag, f.getFundName())
                            .isEqualByComparingTo(exp.holdingProfit());
                }
                if (exp.cumulativeProfit() != null) {
                    assertThat(f.getCumulativeProfit()).as("%s: %s 累计收益", tag, f.getFundName())
                            .isEqualByComparingTo(exp.cumulativeProfit());
                }
                count++;
            }
        }
        assertThat(count).as("%s: 基金总数", tag).isEqualTo(19);

        BigDecimal sum = result.merged().getCategories().stream()
                .flatMap(c -> c.getFunds().stream())
                .map(FundLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).as("%s: 合计金额", tag).isEqualByComparingTo(fx.expectedDedupedSum());
    }
}
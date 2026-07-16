package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import com.fincontrol.service.DedupEngine.DedupInput;
import com.fincontrol.service.DedupEngine.DedupResult;
import com.fincontrol.service.DedupEngine.DedupWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DedupEngine 单测 — 5 维度 dedup 完整覆盖。
 *
 * <p>对应设计文档：{@code docs/phase-1/designs/1a3-dedup-strategy.md} §3.3。
 *
 * <p>维度 B（同 SHA256）暂未实现 — 留 1a.4 接入真实 SHA256 后启用。
 * 当前 DedupEngine 中 B 维度无代码，测试 2 占位说明。
 *
 * <p>Phase 1a.3 启动本文件 + 通过 mvn test 即可视为 DedupEngine 内部闭环。
 */
class DedupEngineTest {

    // ========================================================================
    // 共享 fixture helpers
    // ========================================================================

    /**
     * 构造一只基金的 CategoryBlock。
     */
    private static CategoryBlock fund(String categoryName, String fundName, String amount, String profit) {
        CategoryBlock cb = new CategoryBlock();
        cb.setCategoryName(categoryName);
        FundLine f = new FundLine();
        f.setFundName(fundName);
        f.setAmount(new BigDecimal(amount));
        f.setProfit(new BigDecimal(profit));
        List<FundLine> funds = new ArrayList<>();
        funds.add(f);
        cb.setFunds(funds);
        return cb;
    }

    /**
     * 构造只含 1 只基金的 ParsedAsset。
     */
    private static ParsedAsset singleFundAsset(String conversationId, String date, String category,
                                            String fundName, String amount, String profit) {
        ParsedAsset a = new ParsedAsset();
        a.setConversationId(conversationId);
        a.setSnapshotDate(date);
        List<CategoryBlock> cats = new ArrayList<>();
        cats.add(fund(category, fundName, amount, profit));
        a.setCategories(cats);
        a.setMatchedFunds(new ArrayList<>());
        a.setUnmatchedFunds(new ArrayList<>());
        return a;
    }

    // ========================================================================
    // 维度 A：同 fileId
    // ========================================================================

    @Test
    @DisplayName("A · 同 fileId（3 个：2 个 A + 1 个 B）→ 2 record")
    void dedup_dropsDuplicateFileId() {
        // 3 个 ParsedAsset：conversationId = A, A, B
        // 第二个 A 的 amount 与第一个不同（验证后入优先）
        List<ParsedAsset> input = new ArrayList<>();
        input.add(singleFundAsset("A", "2026-07-16", "权益类", "天弘纳指A", "100.00", "5.00"));
        input.add(singleFundAsset("A", "2026-07-16", "权益类", "天弘纳指A", "200.00", "10.00"));
        input.add(singleFundAsset("B", "2026-07-16", "固收类", "长城短债A", "500.00", "5.00"));

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                input,
                new HashSet<>(),
                LocalDate.of(2026, 7, 16),
                false
        ));

        // 同 fileId 去重 → 2 record
        assertThat(result.report().inputRecordCount()).isEqualTo(3);
        assertThat(result.report().mergedRecordCount()).isEqualTo(2);
        assertThat(result.report().droppedCount()).isEqualTo(1);

        // 验证 fileId=A 的 amount 是后入的 200（不是 100）
        assertThat(result.merged().getCategories())
                .as("应保留 2 个不同 fileId 对应的 fund")
                .hasSize(2);
        boolean foundA = result.merged().getCategories().stream()
                .flatMap(c -> c.getFunds().stream())
                .anyMatch(f -> "天弘纳指A".equals(f.getFundName()) &&
                        new BigDecimal("200.00").compareTo(f.getAmount()) == 0);
        assertThat(foundA).as("维度 A 后入优先：fund 金额应为 200.00").isTrue();
    }

    // ========================================================================
    // 维度 B：同 SHA256（**留 1a.4 接入** — 当前不实现）
    // ========================================================================

    @Test
    @DisplayName("B · 同 SHA256 当前不启用（暂用 conversationId 替代占位）— 1a.4 接入")
    void dedup_sha256NotImplementedYet() {
        // 当前 DedupEngine 没实现 SHA256 dedup（代码注释中明确：留 1a.4）
        // 1a.3 阶段，B 维度行为：与 A 维度退化为"按 fileId 去重"
        // 1a.4 启动时，真实 SHA256 计算需要从 file_system upload 时记录 sha256
        // 本测试 case 占位说明当前状态
        //
        // 输入：3 个 ParsedAsset（全部 fileId=A，但 minimax 返回了不同的 aiRawResponse 文本 → 当前无 B 维度去重）
        List<ParsedAsset> input = new ArrayList<>();
        input.add(singleFundAsset("A", "2026-07-16", "权益类", "天弘纳指A", "100.00", "5.00"));
        input.add(singleFundAsset("A", "2026-07-16", "权益类", "天弘纳指A", "200.00", "10.00"));
        input.add(singleFundAsset("A", "2026-07-16", "权益类", "天弘纳指A", "300.00", "15.00"));

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                input,
                new HashSet<>(),
                LocalDate.of(2026, 7, 16),
                false
        ));

        // 当前实现：A 维度去重后剩 1 record；B 维度未启用
        assertThat(result.report().mergedRecordCount()).isEqualTo(1);
        // 验证 amount 是最后入的 300（仅 A 维度生效，无 B）
        boolean foundA = result.merged().getCategories().stream()
                .flatMap(c -> c.getFunds().stream())
                .anyMatch(f -> new BigDecimal("300.00").compareTo(f.getAmount()) == 0);
        assertThat(foundA).as("B 未启用，amount = 300.00（仅 A 维度去重后最后入）").isTrue();
    }

    // ========================================================================
    // 维度 C：同 fund_name + snapshot_date → 后入优先
    // ========================================================================

    @Test
    @DisplayName("C · 同 fund_name + snapshot_date → 取后入")
    void dedup_mergesSameFundNameDifferentScreens() {
        // 2 个 ParsedAsset：同 fund_name "天弘纳指A"，不同 fileId（不同图）
        // 第一张图 amount=100，第二张图 amount=200
        List<ParsedAsset> input = new ArrayList<>();
        input.add(singleFundAsset("img1", "2026-07-16", "权益类", "天弘纳指A", "100.00", "5.00"));
        input.add(singleFundAsset("img2", "2026-07-16", "权益类", "天弘纳指A", "200.00", "10.00"));

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                input,
                new HashSet<>(),
                LocalDate.of(2026, 7, 16),
                false
        ));

        // 维度 C：同 fund_name → 取后入 amount=200
        assertThat(result.report().inputRecordCount()).isEqualTo(2);
        assertThat(result.report().mergedRecordCount()).isEqualTo(1);
        assertThat(result.report().droppedCount()).isEqualTo(1);

        assertThat(result.merged().getMatchedFunds()).containsExactly("天弘纳指A");
        boolean foundA = result.merged().getCategories().stream()
                .flatMap(c -> c.getFunds().stream())
                .anyMatch(f -> new BigDecimal("200.00").compareTo(f.getAmount()) == 0);
        assertThat(foundA).as("后入优先：fund amount = 200.00").isTrue();
    }

    // ========================================================================
    // 维度 D：同 category 累加
    // ========================================================================

    @Test
    @DisplayName("D · 同 category 多 fund 累加")
    void dedup_aggregatesByCategory() {
        // 2 个 ParsedAsset：
        //   - img1: 权益类 / 天弘纳指A / 600
        //   - img2: 权益类 / 摩根纳指A / 400
        // 期望：合并后 权益类 1 个 category 块，含 2 只基金，categoryTotal = 1000
        List<ParsedAsset> input = new ArrayList<>();
        input.add(singleFundAsset("img1", "2026-07-16", "权益类", "天弘纳指A", "600.00", "30.00"));
        input.add(singleFundAsset("img2", "2026-07-16", "权益类", "摩根纳指A", "400.00", "20.00"));

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                input,
                new HashSet<>(),
                LocalDate.of(2026, 7, 16),
                false
        ));

        assertThat(result.report().mergedRecordCount()).isEqualTo(2);
        // 6 大类合计 = 1000（不含余额类）
        assertThat(result.merged().getSixCategoriesTotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.merged().getTotalAsset()).isEqualByComparingTo(new BigDecimal("1000.00"));
        // 余额类 0
        assertThat(result.merged().getBalanceFund()).isEqualByComparingTo(BigDecimal.ZERO);

        // 权益类 1 个 category block，2 只 fund
        assertThat(result.merged().getCategories())
                .filteredOn(c -> "权益类".equals(c.getCategoryName()))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.getFunds()).hasSize(2);
                    assertThat(c.getCategoryTotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
                });
    }

    @Test
    @DisplayName("D · 同 fund_name 不同 category → 抛 INTERNAL_ERROR（数据冲突）")
    void dedup_throwsOnFundNameCategoryConflict() {
        // 同一 fund "天弘纳指A" 在 img1 属权益类，img2 属商品类 → 数据矛盾
        List<ParsedAsset> input = new ArrayList<>();
        input.add(singleFundAsset("img1", "2026-07-16", "权益类", "天弘纳指A", "100.00", "5.00"));
        input.add(singleFundAsset("img2", "2026-07-16", "商品类", "天弘纳指A", "50.00", "2.00"));

        assertThatThrownBy(() -> new DedupEngine().deduplicate(new DedupInput(
                input,
                new HashSet<>(),
                LocalDate.of(2026, 7, 16),
                false
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).contains("天弘纳指A").contains("权益类").contains("商品类");
                });
    }

    // ========================================================================
    // 维度 E：同 snapshot_date 已存在 → 警告 + 用户确认
    // ========================================================================

    @Test
    @DisplayName("E · 同 snapshot_date 已存在 + 未确认 → 警告 OVERWRITE_REQUIRED")
    void dedup_warnsOnExistingSnapshotDate() {
        // existingFundNamesForSnapshot 非空 → 触发 E
        Set<String> existing = new HashSet<>();
        existing.add("天弘纳指A");
        existing.add("摩根纳指A");

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                List.of(singleFundAsset("img1", "2026-07-16", "权益类", "天弘纳指A", "100.00", "5.00")),
                existing,
                LocalDate.of(2026, 7, 16),
                false
        ));

        assertThat(result.report().warnings()).hasSize(1);
        DedupWarning w = result.report().warnings().get(0);
        assertThat(w.code()).isEqualTo("OVERWRITE_REQUIRED");
        assertThat(w.message()).contains("2026-07-16").contains("2");
    }

    @Test
    @DisplayName("E · 同 snapshot_date 已存在 + confirmedOverwrite=true → 0 warning")
    void dedup_noWarnWhenConfirmedOverwrite() {
        Set<String> existing = new HashSet<>();
        existing.add("天弘纳指A");

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                List.of(singleFundAsset("img1", "2026-07-16", "权益类", "天弘纳指A", "100.00", "5.00")),
                existing,
                LocalDate.of(2026, 7, 16),
                true
        ));

        assertThat(result.report().warnings()).isEmpty();
    }

    // ========================================================================
    // 全维度集成
    // ========================================================================

    @Test
    @DisplayName("全维度混合 — A+C+D 全部生效，E 已确认 → 0 warning")
    void dedup_combinesAllDimensions() {
        // 输入：3 个 ParsedAsset
        //   - img1: 权益类 / 天弘纳指A / 600 + 余额类 / 余额宝 / 100
        //   - img2: 权益类 / 摩根纳指A / 400
        //   - img3: 同 fileId=img1 但 amount 改了（重复上传）
        Set<String> existing = new HashSet<>();
        existing.add("天弘纳指A");
        existing.add("摩根纳指A");

        List<ParsedAsset> input = new ArrayList<>();
        // img1
        ParsedAsset img1 = new ParsedAsset();
        img1.setConversationId("img1");
        img1.setSnapshotDate("2026-07-16");
        List<CategoryBlock> img1Cats = new ArrayList<>();
        img1Cats.add(fund("权益类", "天弘纳指A", "600.00", "30.00"));
        img1Cats.add(fund("余额类", "余额宝", "100.00", "1.00"));
        img1.setCategories(img1Cats);
        input.add(img1);
        // img2
        input.add(singleFundAsset("img2", "2026-07-16", "权益类", "摩根纳指A", "400.00", "20.00"));
        // img3（重复 fileId=img1，A 维度去重）
        ParsedAsset img3 = new ParsedAsset();
        img3.setConversationId("img1");
        img3.setSnapshotDate("2026-07-16");
        List<CategoryBlock> img3Cats = new ArrayList<>();
        img3Cats.add(fund("权益类", "天弘纳指A", "700.00", "40.00"));  // 后入优先
        img3.setCategories(img3Cats);
        input.add(img3);

        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                input,
                existing,
                LocalDate.of(2026, 7, 16),
                true  // confirmedOverwrite
        ));

        // A 维度：img1 和 img3 同 fileId → img3 整张替换 img1（img1 的余额宝 100 随之丢失）→ drop 1
        // C 维度：img3 的天弘纳指A 700 + img2 的摩根纳指A 400 → 2 个 fund
        // D 维度：权益类 1100；余额类 0（img1 被 img3 整张替换，余额宝 100 丢失）
        // E 维度：confirmedOverwrite=true → 0 warning
        assertThat(result.report().inputRecordCount()).isEqualTo(4);
        assertThat(result.report().mergedRecordCount()).isEqualTo(2);
        assertThat(result.report().droppedCount()).isEqualTo(2);
        assertThat(result.report().warnings()).isEmpty();
        assertThat(result.merged().getTotalAsset()).isEqualByComparingTo(new BigDecimal("1100.00"));
        assertThat(result.merged().getSixCategoriesTotal()).isEqualByComparingTo(new BigDecimal("1100.00"));
        assertThat(result.merged().getBalanceFund()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========================================================================
    // 边界
    // ========================================================================

    @Test
    @DisplayName("空输入 → 0 record + 0 warning")
    void dedup_emptyInput() {
        DedupResult result = new DedupEngine().deduplicate(new DedupInput(
                new ArrayList<>(),
                new HashSet<>(),
                LocalDate.of(2026, 7, 16),
                false
        ));

        assertThat(result.report().inputRecordCount()).isEqualTo(0);
        assertThat(result.report().mergedRecordCount()).isEqualTo(0);
        assertThat(result.report().droppedCount()).isEqualTo(0);
        assertThat(result.report().warnings()).isEmpty();
        assertThat(result.merged().getCategories()).isEmpty();
        assertThat(result.merged().getTotalAsset()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

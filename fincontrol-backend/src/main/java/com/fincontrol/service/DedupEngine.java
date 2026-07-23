package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedup 核心引擎（Phase 1a.3 / 1a.4 基础）。
 *
 * <p>5 维度去重：
 * <ul>
 *   <li><b>A. 同 fileId</b>：用户连点 2 次上传 → 保留最后
 *   <li><b>B. 同 SHA256</b>：缓存丢失后重传同张图（**留 1a.4 实现**，当前用 aiRawResponse 简单 hash 替代）
 *   <li><b>C. 同 fund_name + snapshot_date</b>：多图含同只基金 → 取最后一张
 *   <li><b>D. 同 category + snapshot_date</b>：多图同大类金额累加
 *   <li><b>E. 同 snapshot_date 已存在</b>：警告 + 用户确认 overwrite
 * </ul>
 *
 * <p>纯 Java 函数式 — 无 Spring 依赖 — 单测独立可跑。
 *
 * <p>设计详见：{@code docs/phase-1/designs/1a3-dedup-strategy.md}
 */
@Component
public class DedupEngine {

    private static final Logger log = LoggerFactory.getLogger(DedupEngine.class);
    private static final BigDecimal DEFAULT_DISCREPANCY_THRESHOLD_RATIO = new BigDecimal("0.01");

    /** 0.01 表示 1%；由 application.yml 覆盖，纯 Java 单测默认保持 1%。 */
    private final BigDecimal discrepancyThresholdRatio;

    public DedupEngine() {
        this(DEFAULT_DISCREPANCY_THRESHOLD_RATIO);
    }

    @Autowired
    public DedupEngine(@Value("${fincontrol.dedup.discrepancy-threshold-pct:0.01}")
                       BigDecimal discrepancyThresholdRatio) {
        if (discrepancyThresholdRatio == null
                || discrepancyThresholdRatio.compareTo(BigDecimal.ZERO) < 0
                || discrepancyThresholdRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("discrepancy threshold 必须在 0~1 之间");
        }
        this.discrepancyThresholdRatio = discrepancyThresholdRatio;
    }

    // ========================================================================
    // 输入 / 输出 record 类
    // ========================================================================

    public record DedupInput(
            List<ParsedAsset> parsedAssets,                  // 1a.3 confirm 接收的 N 条 ParsedAsset
            Set<String> existingFundNamesForSnapshot,        // 维度 C 检测（库中已存在的 fund_name 集合）
            LocalDate snapshotDate,                          // 维度 E 检测键
            boolean confirmedOverwrite                       // 维度 E 标志
    ) {}

    public record DedupResult(
            ParsedAsset merged,                              // 5 维度去重 + 合并后的最终 ParsedAsset
            DedupReport report
    ) {}

    public record DedupReport(
            int inputRecordCount,
            int mergedRecordCount,
            int droppedCount,
            List<DedupWarning> warnings
    ) {}

    public record DedupWarning(
            String code,                                     // OVERWRITE_REQUIRED | CATEGORY_CONFLICT | DATA_INCOMPLETE
            String message,
            Map<String, Object> context
    ) {}

    // ========================================================================
    // 公共入口
    // ========================================================================

    /**
     * 主 dedup 入口。
     *
     * @return DedupResult.merged 永远是 1 个 ParsedAsset（如全 drop 则 categories=[]）
     * @throws BusinessException 仅在维度 D 出现 fund_name 跨 category 冲突时（数据矛盾）抛 1001
     */
    public DedupResult deduplicate(DedupInput input) {
        if (input.parsedAssets() == null || input.parsedAssets().isEmpty()) {
            return new DedupResult(
                    emptyAsset(input.snapshotDate()),
                    new DedupReport(0, 0, 0, Collections.emptyList())
            );
        }

        List<DedupWarning> warnings = new ArrayList<>();

        // 维度 A：按 fileId 去重，保留最后入
        Map<String, ParsedAsset> byFileId = new LinkedHashMap<>();
        for (ParsedAsset a : input.parsedAssets()) {
            if (a == null) continue;
            byFileId.put(a.getConversationId(), a);
        }
        List<ParsedAsset> dedupedFileId = new ArrayList<>(byFileId.values());

        // 维度 B（占位）— 1a.4 实现：按 aiRawResponse hash 去重
        // 当前简化：每个 ParsedAsset 的 aiMarkdownReport 视为 distinct source
        // 跳过 B（暂用 aiRawResponse hash 替代 SHA256，1a.4 接入真实 SHA256 后启用）
        // 1a.4 替换：Map<String, ParsedAsset> byHash = dedupedFileId.stream()
        //     .collect(Collectors.toMap(a -> sha256(a.getAiMarkdownReport()),
        //                                a -> a,
        //                                (a, b) -> a.amount() >= b.amount() ? a : b));
        List<ParsedAsset> dedupedHash = dedupedFileId;

        // 维度 C + D：按 fund_name + snapshot_date 合并（同名完整记录后入优先；按 category 累加）
        // 1a.8.7：holding_profit / cumulative_profit 双字段同名合并也取后入；跨页只有标题的行
        // （holding 与 cumulative 同时为 null）不能覆盖另一页的完整记录。
        Map<String, MergedFund> mergedFunds = new LinkedHashMap<>();
        for (ParsedAsset a : dedupedHash) {
            if (a.getCategories() == null) continue;
            for (CategoryBlock cat : a.getCategories()) {
                if (cat == null || cat.getFunds() == null) continue;
                String categoryName = trimToNull(cat.getCategoryName());
                for (FundLine fund : cat.getFunds()) {
                    if (fund == null) continue;
                    String key = trimToNull(fund.getFundName());
                    if (key == null) {
                        addIncompleteWarning(warnings, a, categoryName, null,
                                missingFields(categoryName, fund));
                        continue;
                    }

                    BigDecimal holding = effectiveHolding(fund);
                    BigDecimal cumulative = effectiveCumulative(fund, holding);
                    // 1a.8.8：余额类（余额宝/活期/货币基金等）允许 holding_profit=null（Alipay 不显示此列）
                    boolean isBalanceCategory = "余额类".equals(categoryName);
                    boolean complete = categoryName != null
                            && fund.getAmount() != null
                            && (holding != null || isBalanceCategory);
                    MergedFund existing = mergedFunds.get(key);
                    if (!complete) {
                        addIncompleteWarning(warnings, a, categoryName, key,
                                missingFields(categoryName, fund));
                        // 标题行无论先后都不写入 mergedFunds；已有完整记录保持不变。
                        continue;
                    }

                    if (existing == null) {
                        mergedFunds.put(key, new MergedFund(
                                key, categoryName, fund.getAmount(), holding, cumulative,
                                Boolean.TRUE.equals(fund.getIsUserConfirmed()), fund.getConfirmedAt()));
                    } else {
                        // 维度 D 检查：同 fund_name 的两条完整记录若 category 不同，数据冲突。
                        if (!Objects.equals(existing.categoryName, categoryName)) {
                            throw new BusinessException(
                                    ErrorCode.INTERNAL_ERROR,
                                    "fund '" + key + "' 在 " + existing.categoryName + " 与 " + categoryName + " 之间冲突"
                            );
                        }
                        // 维度 C：两条都完整时后入优先（覆盖 amount / holding / cumulative）。
                        existing.amount = fund.getAmount();
                        existing.profit = holding;
                        existing.holdingProfit = holding;
                        existing.cumulativeProfit = cumulative;
                        existing.userConfirmed = Boolean.TRUE.equals(fund.getIsUserConfirmed());
                        existing.confirmedAt = fund.getConfirmedAt();
                    }
                }
            }
        }

        // 维度 D：按 category 分组累加（不修改 fund count — 6 大类各自累加 + 余额类独立）
        Map<String, AggregatedCategory> byCategory = new LinkedHashMap<>();
        for (MergedFund mf : mergedFunds.values()) {
            AggregatedCategory ac = byCategory.computeIfAbsent(
                    mf.categoryName,
                    k -> new AggregatedCategory(k)
            );
            ac.totalAmount = ac.totalAmount.add(mf.amount);
            if (mf.holdingProfit != null) {
                ac.totalHoldingProfit = ac.totalHoldingProfit.add(mf.holdingProfit);
            }
            if (mf.cumulativeProfit != null) {
                ac.totalCumulativeProfit = ac.totalCumulativeProfit.add(mf.cumulativeProfit);
            }
            ac.fundCount++;
        }

        // 构造 merged ParsedAsset
        List<CategoryBlock> mergedCategories = byCategory.values().stream()
                .map(ac -> {
                    CategoryBlock cb = new CategoryBlock();
                    cb.setCategoryName(ac.categoryName);
                    cb.setCategoryTotal(ac.totalAmount);
                    // P6-3 修复：DedupeEngine 重建 CategoryBlock 时复制 fundCount
                    cb.setFundCount(ac.fundCount);
                    // categoryPercentage / targetRatio / deviation 由 AssetSnapshotService 在 1a.3 写入时按 user_config 算
                    List<FundLine> funds = mergedFunds.values().stream()
                            .filter(mf -> Objects.equals(mf.categoryName, ac.categoryName))
                            .map(mf -> {
                                FundLine fl = new FundLine();
                                fl.setFundName(mf.fundName);
                                fl.setAmount(mf.amount);
                                fl.setProfit(mf.profit);
                                fl.setHoldingProfit(mf.holdingProfit);
                                fl.setCumulativeProfit(mf.cumulativeProfit);
                                fl.setIsUserConfirmed(mf.userConfirmed);
                                fl.setConfirmedAt(mf.confirmedAt);
                                return fl;
                            })
                            .collect(Collectors.toList());
                    cb.setFunds(funds);
                    return cb;
                })
                .collect(Collectors.toList());

        ParsedAsset merged = emptyAsset(input.snapshotDate());
        merged.setCategories(mergedCategories);
        merged.setConversationId("dedup-" + input.snapshotDate());
        // 收集所有 fund 名作为 matchedFunds
        List<String> matched = mergedFunds.keySet().stream().sorted().collect(Collectors.toList());
        merged.setMatchedFunds(matched);

        // ==========================================================
        // 1a.9：total_asset 双轨决策 + DISCREPANCY 1% 报警
        // ==========================================================
        // 源 1 (top) — 4 页顶部"总资产"一致时用顶部（语义最准）
        // 源 2 (visible_sum) — 顶部不可用 / 4 页不一致时 fallback 到 deduped fund 加总
        // 报警：|top - dedupedSum| / top > 1% → DISCREPANCY warning
        BigDecimal dedupedSum = mergedFunds.values().stream()
                .map(mf -> mf.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 过滤 4 页 non-null topTotalAsset
        List<BigDecimal> tops = dedupedHash.stream()
                .map(ParsedAsset::getTotalAsset)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        BigDecimal finalTotal;
        String totalSource;
        if (!tops.isEmpty() && tops.stream().allMatch(t -> t.compareTo(tops.get(0)) == 0)) {
            // 4 页 top 一致 → 用 top
            finalTotal = tops.get(0);
            totalSource = "top";
        } else {
            // 4 页 top 不一致 / 全部为 null → fallback deduped sum
            finalTotal = dedupedSum;
            totalSource = "visible_sum";
            if (!tops.isEmpty()) {
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("tops", tops);
                ctx.put("dedupedSum", dedupedSum);
                warnings.add(new DedupWarning(
                        "TOP_INCONSISTENT",
                        "4 页顶部 total_asset 不一致 (" + tops.stream().map(BigDecimal::toPlainString).collect(Collectors.joining(",")) + ")；fallback 到 deduped sum=" + dedupedSum,
                        ctx
                ));
            }
        }
        merged.setTotalAsset(finalTotal);
        merged.setTotalAssetSource(totalSource);

        // 校验：top vs dedupedSum 偏差超过可配置阈值 → DISCREPANCY warning（不阻塞）
        if (!tops.isEmpty()) {
            BigDecimal refTop = tops.get(0);
            BigDecimal diff = refTop.subtract(dedupedSum).abs();
            BigDecimal diffRatio = refTop.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : diff.divide(refTop, 8, RoundingMode.HALF_UP);
            if (refTop.compareTo(BigDecimal.ZERO) != 0
                    && diffRatio.compareTo(discrepancyThresholdRatio) > 0) {
                BigDecimal diffPct = diffRatio.multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal thresholdPct = discrepancyThresholdRatio.multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros();
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("top", refTop);
                ctx.put("dedupedSum", dedupedSum);
                ctx.put("diffRatio", diffRatio);
                ctx.put("diffPct", diffPct);
                ctx.put("thresholdRatio", discrepancyThresholdRatio);
                ctx.put("thresholdPct", thresholdPct);
                warnings.add(new DedupWarning(
                        "DISCREPANCY",
                        "顶部总资产 " + refTop + " 与 deduped sum " + dedupedSum
                                + " 偏差 " + diffPct + "% > " + thresholdPct.toPlainString() + "%阈值",
                        ctx
                ));
            }
        }

        // matchedFunds 已经在前面设置
        merged.setUnmatchedFunds(Collections.emptyList());
        // 六大类合计（计算合并后的 6 大类总金额）
        BigDecimal sixTotal = byCategory.values().stream()
                .filter(ac -> !Objects.equals(ac.categoryName, "余额类"))
                .map(ac -> ac.totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        merged.setSixCategoriesTotal(sixTotal);
        // 余额类余额
        AggregatedCategory yue = byCategory.get("余额类");
        if (yue != null) {
            merged.setBalanceFund(yue.totalAmount);
        }

        // 维度 E：同 snapshot_date 已存在 → 警告（除非 confirmedOverwrite=true）
        if (input.existingFundNamesForSnapshot() != null && !input.existingFundNamesForSnapshot().isEmpty()) {
            if (!input.confirmedOverwrite()) {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("snapshotDate", input.snapshotDate().toString());
                ctx.put("existingFundCount", input.existingFundNamesForSnapshot().size());
                warnings.add(new DedupWarning(
                        "OVERWRITE_REQUIRED",
                        "snapshot_date " + input.snapshotDate() + " 已存在 " + input.existingFundNamesForSnapshot().size() + " 条基金记录；设 confirmedOverwrite=true 可覆盖",
                        ctx
                ));
            }
            // confirmedOverwrite=true → 不警告（覆盖已确认）
        }

        // 构造报告
        int inputCount = input.parsedAssets().stream()
                .filter(Objects::nonNull)
                .mapToInt(a -> a.getCategories() == null ? 0 :
                        a.getCategories().stream()
                                .filter(Objects::nonNull)
                                .mapToInt(c -> c.getFunds() == null ? 0 : c.getFunds().size())
                                .sum())
                .sum();
        int mergedCount = mergedFunds.size();
        DedupReport report = new DedupReport(
                inputCount,
                mergedCount,
                inputCount - mergedCount,
                warnings
        );

        log.debug("dedup: input={} merged={} dropped={} warnings={}",
                inputCount, mergedCount, inputCount - mergedCount, warnings.size());

        return new DedupResult(merged, report);
    }

    // ========================================================================
    // 内部辅助
    // ========================================================================

    private static void addIncompleteWarning(List<DedupWarning> warnings,
                                             ParsedAsset asset,
                                             String categoryName,
                                             String fundName,
                                             List<String> missingFields) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("conversationId", asset == null ? null : asset.getConversationId());
        context.put("fundName", fundName);
        context.put("categoryName", categoryName);
        context.put("missingFields", missingFields);
        warnings.add(new DedupWarning(
                "DATA_INCOMPLETE",
                "忽略不完整基金记录 fund='" + (fundName == null ? "<missing>" : fundName)
                        + "' missing=" + missingFields,
                context));
    }

    private static List<String> missingFields(String categoryName, FundLine fund) {
        List<String> missing = new ArrayList<>();
        if (fund == null || trimToNull(fund.getFundName()) == null) missing.add("fundName");
        if (categoryName == null) missing.add("categoryName");
        if (fund == null || fund.getAmount() == null) missing.add("amount");
        if (fund == null || fund.getProfit() == null) missing.add("profit");
        return missing;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 空 ParsedAsset（用于 0 record 输入场景）。
     */
    private static ParsedAsset emptyAsset(LocalDate snapshotDate) {
        ParsedAsset a = new ParsedAsset();
        a.setConversationId("");
        a.setSnapshotDate(snapshotDate == null ? null : snapshotDate.toString());
        a.setCategories(new ArrayList<>());
        a.setMatchedFunds(new ArrayList<>());
        a.setUnmatchedFunds(new ArrayList<>());
        a.setTotalAsset(BigDecimal.ZERO);
        a.setSixCategoriesTotal(BigDecimal.ZERO);
        a.setBalanceFund(BigDecimal.ZERO);
        return a;
    }

    /**
     * 内部 record：fund 级聚合中间态。1a.8.7 起 holding / cumulative 双字段。
     */
    private static final class MergedFund {
        final String fundName;
        String categoryName;
        BigDecimal amount;
        BigDecimal profit;
        BigDecimal holdingProfit;
        BigDecimal cumulativeProfit;
        boolean userConfirmed;
        LocalDateTime confirmedAt;

        MergedFund(String fundName, String categoryName,
                   BigDecimal amount, BigDecimal holdingProfit, BigDecimal cumulativeProfit,
                   boolean userConfirmed, LocalDateTime confirmedAt) {
            this.fundName = fundName;
            this.categoryName = categoryName;
            this.amount = amount;
            this.holdingProfit = holdingProfit;
            this.cumulativeProfit = cumulativeProfit;
            this.userConfirmed = userConfirmed;
            this.confirmedAt = confirmedAt;
            // 1a.8.7 兼容：profit 同步 = holdingProfit
            this.profit = holdingProfit;
        }
    }

    /**
     * 内部 record：category 级聚合中间态。1a.8.7 累计 holding + cumulative。
     */
    private static final class AggregatedCategory {
        final String categoryName;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalHoldingProfit = BigDecimal.ZERO;
        BigDecimal totalCumulativeProfit = BigDecimal.ZERO;
        int fundCount = 0;

        AggregatedCategory(String categoryName) {
            this.categoryName = categoryName;
        }
    }

    private static BigDecimal effectiveHolding(FundLine fund) {
        if (fund == null) return null;
        if (fund.getHoldingProfit() != null) return fund.getHoldingProfit();
        return fund.getProfit();
    }

    private static BigDecimal effectiveCumulative(FundLine fund, BigDecimal holdingFallback) {
        if (fund == null) return holdingFallback;
        return fund.getCumulativeProfit() != null ? fund.getCumulativeProfit() : holdingFallback;
    }
}

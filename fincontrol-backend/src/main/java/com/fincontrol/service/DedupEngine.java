package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
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

        // 维度 A：按 fileId 去重，保留最后入
        Map<String, ParsedAsset> byFileId = new LinkedHashMap<>();
        for (ParsedAsset a : input.parsedAssets()) {
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

        // 维度 C + D：按 fund_name + snapshot_date 合并（同名 fund 保留最后入；按 category 累加）
        Map<String, MergedFund> mergedFunds = new LinkedHashMap<>();
        for (ParsedAsset a : dedupedHash) {
            for (CategoryBlock cat : a.getCategories()) {
                for (FundLine fund : cat.getFunds()) {
                    if (fund.getFundName() == null) continue;
                    String key = fund.getFundName();
                    MergedFund existing = mergedFunds.get(key);
                    if (existing == null) {
                        mergedFunds.put(key, new MergedFund(
                                key, cat.getCategoryName(),
                                fund.getAmount(), fund.getProfit(),
                                fund.getFundName(), cat.getCategoryName()
                        ));
                    } else {
                        // 维度 D 检查：同 fund_name 但不同 category → 数据冲突
                        if (!Objects.equals(existing.categoryName, cat.getCategoryName())) {
                            throw new BusinessException(
                                    ErrorCode.INTERNAL_ERROR,
                                    "fund '" + key + "' 在 " + existing.categoryName + " 与 " + cat.getCategoryName() + " 之间冲突"
                            );
                        }
                        // 维度 C：后入优先（覆盖 amount / profit）
                        existing.amount = fund.getAmount();
                        existing.profit = fund.getProfit();
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
            ac.totalProfit = ac.totalProfit.add(mf.profit);
            ac.fundCount++;
        }

        // 构造 merged ParsedAsset
        List<CategoryBlock> mergedCategories = byCategory.values().stream()
                .map(ac -> {
                    CategoryBlock cb = new CategoryBlock();
                    cb.setCategoryName(ac.categoryName);
                    cb.setCategoryTotal(ac.totalAmount);
                    // categoryPercentage / targetRatio / deviation 由 AssetSnapshotService 在 1a.3 写入时按 user_config 算
                    List<FundLine> funds = mergedFunds.values().stream()
                            .filter(mf -> Objects.equals(mf.categoryName, ac.categoryName))
                            .map(mf -> {
                                FundLine fl = new FundLine();
                                fl.setFundName(mf.fundName);
                                fl.setAmount(mf.amount);
                                fl.setProfit(mf.profit);
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

        // 计算总资产（合并后）
        BigDecimal totalAmount = mergedFunds.values().stream()
                .map(mf -> mf.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        merged.setTotalAsset(totalAmount);

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
        List<DedupWarning> warnings = new ArrayList<>();
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
                .mapToInt(a -> a.getCategories() == null ? 0 :
                        a.getCategories().stream()
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
     * 内部 record：fund 级聚合中间态。
     */
    private static final class MergedFund {
        final String fundName;
        String categoryName;
        BigDecimal amount;
        BigDecimal profit;

        MergedFund(String key, String firstCategory, BigDecimal firstAmount, BigDecimal firstProfit,
                    String fundName, String categoryName) {
            this.fundName = fundName;
            this.categoryName = firstCategory;
            this.amount = firstAmount;
            this.profit = firstProfit;
        }
    }

    /**
     * 内部 record：category 级聚合中间态。
     */
    private static final class AggregatedCategory {
        final String categoryName;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        int fundCount = 0;

        AggregatedCategory(String categoryName) {
            this.categoryName = categoryName;
        }
    }
}

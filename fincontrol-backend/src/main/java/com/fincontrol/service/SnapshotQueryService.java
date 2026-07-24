package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.category.CategoryMapMatchItem;
import com.fincontrol.dto.category.CategoryMapMatchResponse;
import com.fincontrol.dto.snapshot.SnapshotByDateResponse;
import com.fincontrol.dto.snapshot.SnapshotCategorySummary;
import com.fincontrol.dto.snapshot.SnapshotFundDetail;
import com.fincontrol.dto.snapshot.SnapshotHistoryItem;
import com.fincontrol.dto.snapshot.SnapshotHistoryResponse;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetRawQueryMapper;
import com.fincontrol.mapper.SnapshotMetaMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 1a.4 快照查询服务（[api-contract.md §3.1/§3.2](#)）。
 *
 * <p>latest 与 latest/detail 共用同一查询入口；只读，不写库。
 *
 * <p>1b.3 补救：
 * <ul>
 *   <li>R3：currentDate 来自 {@code snapshot_meta.is_current}，与首页其它数据源严格一致。</li>
 *   <li>R4：实际比例与偏差按权威金额重算；目标比例从 {@code user_config.target_ratios} 加载，
 *       余额类排除在六大类配置之外并显示为 {@code —}。</li>
 * </ul>
 */
@Service
public class SnapshotQueryService {

    /** 6 大类合计除以自身的归一化基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 1b4pr6b-recovery：6 大类 canonical 固定顺序，用于 buildCanonicalSummaries
     * 使响应 categories 数组始终齐全 6 行，即使用原始 AssetSnapshot 都被 AI 误猜也补齐。
     */
    private static final List<String> CANONICAL_SIX_CATEGORIES = List.of(
            "货币类", "固收类", "商品类", "A股权益类", "海外权益类", "港股大中华类");

    /** 余额类单独处理。 */
    private static final String BALANCE_CATEGORY = "余额类";

    private static final Logger log = LoggerFactory.getLogger(SnapshotQueryService.class);

    private final AssetSnapshotMapper assetSnapshotMapper;
    private final AssetRawMapper assetRawMapper;
    private final AssetRawQueryMapper assetRawQueryMapper;
    private final SnapshotMetaMapper snapshotMetaMapper; // 1b.3.4 决策 27
    private final UserConfigService userConfigService;
    private final CurrentSnapshotContext currentSnapshotContext;
    private final CategoryMapService categoryMapService; // 1b4pr6b Fix D：注入大类映射服务用于 user_correct 覆盖

    public SnapshotQueryService(AssetSnapshotMapper assetSnapshotMapper,
                                AssetRawMapper assetRawMapper,
                                AssetRawQueryMapper assetRawQueryMapper,
                                SnapshotMetaMapper snapshotMetaMapper,
                                UserConfigService userConfigService,
                                CurrentSnapshotContext currentSnapshotContext,
                                CategoryMapService categoryMapService) {
        this.assetSnapshotMapper = assetSnapshotMapper;
        this.assetRawMapper = assetRawMapper;
        this.assetRawQueryMapper = assetRawQueryMapper;
        this.snapshotMetaMapper = snapshotMetaMapper;
        this.userConfigService = userConfigService;
        this.currentSnapshotContext = currentSnapshotContext;
        this.categoryMapService = categoryMapService;
    }

    /**
     * 查询 user 最新一版快照。
     *
     * @param userId       用户 ID（来自 X-User-Id，默认 1）
     * @param includeDetail 是否附加每只基金明细
     * @param includeBalance 是否包含余额类汇总
     */
    public SnapshotLatestResponse getLatest(Long userId, boolean includeDetail, boolean includeBalance) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        // 1b.3.4 决策 27：找当前快照日期：查 snapshot_meta.is_current=true 的 snapshot_date
        LocalDate latestDate = currentSnapshotContext.resolveCurrentDate(userId);
        if (latestDate == null) {
            return null;
        }
        List<AssetSnapshot> snapshots = Optional.ofNullable(
                assetSnapshotMapper.selectLatestByUserAndDate(userId, latestDate))
                .orElse(Collections.emptyList());
        if (snapshots.isEmpty()) {
            return null;
        }
        return buildLatestResponse(userId, latestDate, snapshots, includeDetail, includeBalance);
    }

    private SnapshotLatestResponse buildLatestResponse(Long userId,
                                                     LocalDate snapshotDate,
                                                     List<AssetSnapshot> snapshots,
                                                     boolean includeDetail,
                                                     boolean includeBalance) {
        Map<String, BigDecimal> targetRatios = userConfigService.loadSixCategoryTargetRatios(userId);
        // 1b4pr6b Fix D 完整实现：加载 user_correct 覆盖映射
        Map<String, String> userCorrectMap = loadUserCorrectOverrides(userId, snapshotDate);
        // 1b4pr6b Fix D 完整实现：按 effective category（override > AI 原猜）重算六类总额
        BigDecimal sixTotal = recomputeSixTotal(userId, snapshotDate, userCorrectMap);
        BigDecimal balanceTotal = includeBalance
                ? recomputeBalanceTotal(userId, snapshotDate, userCorrectMap)
                : BigDecimal.ZERO;
        LocalDateTime confirmedAt = null;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
        }
        // 1b4pr6b-recovery：始终输出 7 大类（6类+可选余额类）summary，含由 user_correct 引入的新类
        // 不再只迭代原始 snapshots，新以 CANONICAL_SIX_CATEGORIES 为全集 key
        List<SnapshotCategorySummary> summaries = buildCanonicalSummaries(
                userId, snapshotDate, snapshots,
                sixTotal, balanceTotal, userCorrectMap, targetRatios,
                includeBalance, includeDetail);
        BigDecimal sixWithBalance = sixTotal.add(balanceTotal);
        return SnapshotLatestResponse.builder()
                .snapshotDate(snapshotDate)
                .snapshotConfirmedAt(confirmedAt)
                .sixCategoriesTotal(sixTotal)
                .balanceFund(balanceTotal)
                .totalAssetWithBalance(sixWithBalance)
                .categories(summaries)
                .build();
    }

    /**
     * 1b4pr6b Fix D：加载 user 的 user_correct 覆盖映射
     * @return fundName -> overriddenCategory（仅包含 source=user_correct 且 is_latest=true 的行）
     */
    private Map<String, String> loadUserCorrectOverrides(Long userId, LocalDate snapshotDate) {
        // 从 asset_raw 读取 user+date 下所有 is_latest=1 的 fund_name
        Set<String> fundNames = assetRawMapper.selectFundNamesByUserAndDate(userId, snapshotDate);
        if (fundNames == null || fundNames.isEmpty()) {
            return Collections.emptyMap();
        }
        String csv = String.join(",", fundNames);
        try {
            CategoryMapMatchResponse resp = categoryMapService.match(userId, csv);
            Map<String, String> overrides = new HashMap<>(resp.getMatchedFunds().size());
            for (CategoryMapMatchItem item : resp.getMatchedFunds()) {
                // 只采用 user_correct / user_manual 覆盖，ai_guess 不算 override
                if ("user_correct".equals(item.getSource()) || "user_manual".equals(item.getSource())) {
                    overrides.put(item.getFundName(), item.getCategory());
                }
            }
            return overrides;
        } catch (Exception e) {
            log.warn("1b4pr6b Fix D: loadUserCorrectOverrides failed, userId={} snapshotDate={}",
                    userId, snapshotDate, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 1b4pr6b Fix D：返回 userId+date 下所有 AssetSnapshot
     * （复用 snapshots 参数，供后续 raw 查询使用）
     */
    private List<AssetSnapshot> collectAllSnapshots(Long userId, LocalDate snapshotDate) {
        return assetSnapshotMapper.selectLatestByUserAndDate(userId, snapshotDate) == null
                ? Collections.emptyList()
                : assetSnapshotMapper.selectLatestByUserAndDate(userId, snapshotDate);
    }

    /**
     * 1b4pr6b Fix D：按 effective category（override > AI 原猜）重新聚合 asset_raw
     * @param excludeBalanceClass true → 不含余额类（用于六类）；false → 仅含余额类（用于余额）
     * @return Map<categoryName, totalAmount>
     */
    private Map<String, BigDecimal> aggregateByEffectiveCategory(Long userId,
                                                                  LocalDate snapshotDate,
                                                                  Map<String, String> userCorrectMap,
                                                                  boolean excludeBalanceClass) {
        Map<String, BigDecimal> result = new HashMap<>();
        List<AssetRaw> rows = Optional.ofNullable(
                assetRawQueryMapper.selectCurrentByUserAndDate(userId, snapshotDate))
                .orElse(Collections.emptyList());
        for (AssetRaw row : rows) {
            String originalCategory = row.getCategory();
            boolean isBalance = "余额类".equals(originalCategory);
            if (excludeBalanceClass && isBalance) continue;
            if (!excludeBalanceClass && !isBalance) continue;
            // effective category = userCorrectMap.get(fundName) || originalCategory
            String effective = userCorrectMap.getOrDefault(row.getFundName(), originalCategory);
            BigDecimal amount = nz(row.getAmount());
            result.merge(effective, amount, BigDecimal::add);
        }
        return result;
    }

    /**
     * 1b4pr6b-recovery (2026-07-25 03:30+)：
     * 始终构造 6 大类 + （可选余额类）的 SnapshotCategorySummary。
     * 即使用原始 AssetSnapshot 没某一类（例如 AI 将固收类三只都误判为 A 股权益类），
     * 也会补一行 total=0 / fundCount=0 的 summary 使前端补齐。
     * <p>与 Fix D 差异：不再只迭代原始 snapshots，新以 CANONICAL_SIX_CATEGORIES 为全集 key。
     *
     * @param snapshots         原始 AssetSnapshot（仅作为 noOverride 路径的中含 totalAmount / category 来源）
     * @param sixTotal          已重算后的六大类合计（recomputeSixTotal 输出）
     * @param balanceTotal      已重算后的余额类合计（recomputeBalanceTotal 输出；未含余额类查0）
     * @param userCorrectMap    user_correct 覆盖映射（可能为空）
     * @param targetRatios      六类目标比例
     * @param includeBalance    是否含余额类行
     * @param includeDetail     是否含 funds 明细
     */
    private List<SnapshotCategorySummary> buildCanonicalSummaries(
            Long userId,
            LocalDate snapshotDate,
            List<AssetSnapshot> snapshots,
            BigDecimal sixTotal,
            BigDecimal balanceTotal,
            Map<String, String> userCorrectMap,
            Map<String, BigDecimal> targetRatios,
            boolean includeBalance,
            boolean includeDetail) {
        boolean hasOverride = userCorrectMap != null && !userCorrectMap.isEmpty();

        // no-override 路径：从原始 AssetSnapshot 取 categoryTotal
        Map<String, BigDecimal> totalByCategoryFromSnap = new HashMap<>();
        for (AssetSnapshot s : snapshots) {
            totalByCategoryFromSnap.merge(s.getCategory(), nz(s.getTotalAmount()), BigDecimal::add);
        }
        // override 路径：提前采 effective / fund count 集
        Map<String, BigDecimal> effectiveSixByCategory = hasOverride
                ? aggregateByEffectiveCategory(userId, snapshotDate, userCorrectMap, true)
                : Collections.emptyMap();
        Map<String, BigDecimal> effectiveBalanceByCategory = hasOverride
                ? aggregateByEffectiveCategory(userId, snapshotDate, userCorrectMap, false)
                : Collections.emptyMap();
        Map<String, Integer> effectiveFundCount = hasOverride
                ? countFundsByEffectiveCategory(userId, snapshotDate, userCorrectMap)
                : Collections.emptyMap();

        List<SnapshotCategorySummary> summaries = new ArrayList<>(CANONICAL_SIX_CATEGORIES.size() + 1);

        // 6 大类：始终输出（即使为 0）
        for (String category : CANONICAL_SIX_CATEGORIES) {
            BigDecimal total = hasOverride
                    ? effectiveSixByCategory.getOrDefault(category, BigDecimal.ZERO)
                    : totalByCategoryFromSnap.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal actualRatio = sixTotal.signum() == 0
                    ? BigDecimal.ZERO
                    : total.multiply(HUNDRED).divide(sixTotal, 2, RoundingMode.HALF_UP);
            BigDecimal targetRatio = targetRatios.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal deviation = actualRatio.subtract(targetRatio);
            Integer fundCount = hasOverride
                    ? effectiveFundCount.getOrDefault(category, 0)
                    : assetRawMapper.countFundsByUserAndDateAndCategory(userId, snapshotDate, category);
            List<SnapshotFundDetail> funds = includeDetail
                    ? toFundDetailsWithOverride(userId, snapshotDate, category, userCorrectMap)
                    : null;
            summaries.add(SnapshotCategorySummary.builder()
                    .categoryName(category)
                    .categoryTotal(total)
                    .actualRatio(actualRatio)
                    .targetRatio(targetRatio)
                    .deviation(deviation)
                    .fundCount(fundCount)
                    .funds(funds)
                    .build());
        }
        // 余额类：仅 includeBalance 时输出
        if (includeBalance) {
            BigDecimal total = hasOverride
                    ? effectiveBalanceByCategory.getOrDefault(BALANCE_CATEGORY, BigDecimal.ZERO)
                    : totalByCategoryFromSnap.getOrDefault(BALANCE_CATEGORY, BigDecimal.ZERO);
            // 后端可能传入 0 余额类，但仍需以 user_correct 后为准
            if (!hasOverride && balanceTotal != null) {
                total = balanceTotal; // 迫位为最终送来的 balanceTotal
            }
            Integer fundCount = hasOverride
                    ? effectiveFundCount.getOrDefault(BALANCE_CATEGORY, 0)
                    : assetRawMapper.countFundsByUserAndDateAndCategory(userId, snapshotDate, BALANCE_CATEGORY);
            List<SnapshotFundDetail> funds = includeDetail
                    ? toFundDetailsWithOverride(userId, snapshotDate, BALANCE_CATEGORY, userCorrectMap)
                    : null;
            summaries.add(SnapshotCategorySummary.builder()
                    .categoryName(BALANCE_CATEGORY)
                    .categoryTotal(total)
                    .actualRatio(null)    // 余额类不参与六大类配置
                    .targetRatio(null)
                    .deviation(null)
                    .fundCount(fundCount)
                    .funds(funds)
                    .build());
        }
        return summaries;
    }

    /**
     * 1b4pr6b Fix D：按 effective category 计算六大类（不含余额类）总金额
     * 取代依赖 AssetSnapshot.totalAmount 的旧逻辑。
     */
    private BigDecimal recomputeSixTotal(Long userId, LocalDate snapshotDate, Map<String, String> userCorrectMap) {
        // 无 user_correct 覆盖时,直接用 AssetSnapshot 总额(兼容 1a.4 老测试)
        if (userCorrectMap == null || userCorrectMap.isEmpty()) {
            List<AssetSnapshot> snaps = Optional.ofNullable(
                    assetSnapshotMapper.selectLatestByUserAndDate(userId, snapshotDate))
                    .orElse(Collections.emptyList());
            return snaps.stream()
                    .filter(s -> !"余额类".equals(s.getCategory()))
                    .map(s -> nz(s.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        // 有 user_correct 覆盖:按 effective raw 重新聚合
        Map<String, BigDecimal> byCat = aggregateByEffectiveCategory(userId, snapshotDate, userCorrectMap, true);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amt : byCat.values()) {
            sum = sum.add(amt);
        }
        return sum;
    }

    /**
     * 1b4pr6b Fix D：按 effective category 计算余额类总金额
     */
    private BigDecimal recomputeBalanceTotal(Long userId, LocalDate snapshotDate, Map<String, String> userCorrectMap) {
        // 无 user_correct 覆盖:从 AssetSnapshot 取余额类总额
        if (userCorrectMap == null || userCorrectMap.isEmpty()) {
            List<AssetSnapshot> snaps = Optional.ofNullable(
                    assetSnapshotMapper.selectLatestByUserAndDate(userId, snapshotDate))
                    .orElse(Collections.emptyList());
            return snaps.stream()
                    .filter(s -> "余额类".equals(s.getCategory()))
                    .map(s -> nz(s.getTotalAmount()))
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
        }
        // 有 user_correct 覆盖:按 effective raw 重新聚合
        Map<String, BigDecimal> byCat = aggregateByEffectiveCategory(userId, snapshotDate, userCorrectMap, false);
        return byCat.getOrDefault("余额类", BigDecimal.ZERO);
    }

    /**
     * 1b4pr6b Fix D：按 effective category 计算基金数
     */
    private Map<String, Integer> countFundsByEffectiveCategory(Long userId,
                                                                LocalDate snapshotDate,
                                                                Map<String, String> userCorrectMap) {
        Map<String, Integer> result = new HashMap<>();
        List<AssetRaw> rows = Optional.ofNullable(
                assetRawQueryMapper.selectCurrentByUserAndDate(userId, snapshotDate))
                .orElse(Collections.emptyList());
        for (AssetRaw row : rows) {
            String originalCategory = row.getCategory();
            String effective = userCorrectMap.getOrDefault(row.getFundName(), originalCategory);
            result.merge(effective, 1, Integer::sum);
        }
        return result;
    }

    private SnapshotCategorySummary toCategorySummary(Long userId,LocalDate snapshotDate,AssetSnapshot snap,BigDecimal sixTotal,
                                                     BigDecimal balanceTotal,
                                                     Map<String, String> userCorrectMap,
                                                     Map<String, BigDecimal> targetRatios,
                                                     boolean includeDetail) {
        String category = snap.getCategory();
        // 1b4pr6b Fix D：如果这只基金被 user_correct 改到其它类，本 summary 的 category 应为 effective
        // 否则保留原 AI 猜分类。但要保证在 7 canonical + 余额类 范围内（避免脏数据）
        String originalCategory = category;
        boolean isBalance = "余额类".equals(originalCategory);
        // 1b4pr6b Fix D：用 effective 重新算 categoryTotal
        // （从 asset_raw 按 effective 重新聚合出来的 Map 中取值）
        Map<String, BigDecimal> effectiveByCat;
        Map<String, Integer> effectiveCount;
        BigDecimal total;
        if (userCorrectMap == null || userCorrectMap.isEmpty()) {
            // 无 user_correct 覆盖:直接用 AssetSnapshot 总额(兼容 1a.4 老测试)
            total = nz(snap.getTotalAmount());
            if (isBalance) {
                effectiveCount = Map.of("余额类", 
                    assetRawMapper.countFundsByUserAndDateAndCategory(userId, snapshotDate, "余额类"));
            } else {
                effectiveCount = Map.of(category, 
                    assetRawMapper.countFundsByUserAndDateAndCategory(userId, snapshotDate, category));
            }
            effectiveByCat = Collections.emptyMap();
        } else {
            if (isBalance) {
                effectiveByCat = aggregateByEffectiveCategory(userId, snapshotDate, userCorrectMap, false);
                effectiveCount = Map.of("余额类", effectiveByCat.containsKey("余额类")
                        ? assetRawMapper.countFundsByUserAndDateAndCategory(userId, snapshotDate, "余额类")
                        : 0);
            } else {
                effectiveByCat = aggregateByEffectiveCategory(userId, snapshotDate, userCorrectMap, true);
                effectiveCount = countFundsByEffectiveCategory(userId, snapshotDate, userCorrectMap);
            }
            total = effectiveByCat.getOrDefault(category, BigDecimal.ZERO);
        }
        // 如果该 snap 的 category 已无任何基金（全部被改走了），total = 0
        // 这时仍展示该 category（act as placeholder）但 fundCount=0
        Integer fundCount = effectiveCount.getOrDefault(category, 0);
        BigDecimal actualRatio;
        BigDecimal targetRatio;
        if ("余额类".equals(category)) {
            // 余额类不参与六大类配置
            actualRatio = null;
            targetRatio = null;
        } else if (sixTotal.signum() == 0) {
            actualRatio = BigDecimal.ZERO;
            targetRatio = targetRatios.getOrDefault(category, BigDecimal.ZERO);
        } else {
            actualRatio = total.multiply(HUNDRED).divide(sixTotal, 2, RoundingMode.HALF_UP);
            targetRatio = targetRatios.getOrDefault(category, BigDecimal.ZERO);
        }
        BigDecimal deviation = (actualRatio == null)
                ? null
                : actualRatio.subtract(targetRatio);
        List<SnapshotFundDetail> funds = includeDetail
                ? toFundDetailsWithOverride(userId, snapshotDate, category, userCorrectMap)
                : null;
        return SnapshotCategorySummary.builder()
                .categoryName(category)
                .categoryTotal(total)
                .actualRatio(actualRatio)
                .targetRatio(targetRatio)
                .deviation(deviation)
                .fundCount(fundCount)
                .funds(funds)
                .build();
    }

    /**
     * 1b4pr6b Fix D：基金明细带上 user_correct 后的 effective category
     * （funds 列表里的 category 字段是 effective category；amount 不变）
     */
    private List<SnapshotFundDetail> toFundDetailsWithOverride(Long userId,
                                                                 LocalDate snapshotDate,
                                                                 String category,
                                                                 Map<String, String> userCorrectMap) {
        List<AssetRaw> rows = Optional.ofNullable(
                assetRawQueryMapper.selectCurrentByUserAndDate(userId, snapshotDate))
                .orElse(Collections.emptyList());
        List<SnapshotFundDetail> details = new ArrayList<>();
        for (AssetRaw row : rows) {
            if (Boolean.FALSE.equals(row.getIsLatest())) continue;
            String effective = userCorrectMap.getOrDefault(row.getFundName(), row.getCategory());
            // 只展示本 category 的基金（包括 effective 落在此 category 的）
            if (!effective.equals(category)) continue;
            BigDecimal holding = nz(row.getHoldingProfit() != null ? row.getHoldingProfit() : row.getProfit());
            BigDecimal cumulative = nz(row.getCumulativeProfit() != null ? row.getCumulativeProfit() : holding);
            details.add(SnapshotFundDetail.builder()
                    .fundName(row.getFundName())
                    .amount(nz(row.getAmount()))
                    .profit(holding)
                    .holdingProfit(holding)
                    .cumulativeProfit(cumulative)
                    .category(effective)
                    .build());
        }
        return details;
    }

    private List<SnapshotFundDetail> toFundDetails(Long userId, LocalDate snapshotDate, String category) {
        List<AssetRaw> rows = Optional.ofNullable(
                assetRawMapper.selectByUserAndDateAndCategory(userId, snapshotDate, category))
                .orElse(Collections.emptyList());
        List<SnapshotFundDetail> details = new ArrayList<>(rows.size());
        for (AssetRaw row : rows) {
            if (Boolean.FALSE.equals(row.getIsLatest())) {
                continue;
            }
            BigDecimal holding = nz(row.getHoldingProfit() != null ? row.getHoldingProfit() : row.getProfit());
            BigDecimal cumulative = nz(row.getCumulativeProfit() != null ? row.getCumulativeProfit() : holding);
            details.add(SnapshotFundDetail.builder()
                    .fundName(row.getFundName())
                    .amount(nz(row.getAmount()))
                    .profit(holding)
                    .holdingProfit(holding)
                    .cumulativeProfit(cumulative)
                    .category(row.getCategory())
                    .build());
        }
        return details;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }

    @SuppressWarnings("unused")
    private static BigDecimal safeRounding(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unused")
    private static boolean notEmpty(String s) {
        return s != null && !Objects.requireNonNull(s).isEmpty();
    }

    @SuppressWarnings("unused")
    private static BigDecimal percent(BigDecimal value, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
    }

    // ========================================================================
    // 1a.4 Slice B：指定日期 + history
    // ========================================================================

    /**
     * 查询 user 指定日期的快照（[api-contract.md §3.3](#)）。
     * <p>该日期无 snapshot 时抛 {@link BusinessException} {@code 2001}。
     */
    public SnapshotByDateResponse getByDate(Long userId, LocalDate date, boolean includeBalance) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        if (date == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "snapshotDate 必填");
        }
        List<AssetSnapshot> snapshots = Optional.ofNullable(
                assetSnapshotMapper.selectLatestByUserAndDate(userId, date))
                .orElse(Collections.emptyList());
        if (snapshots.isEmpty()) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND,
                    "该日期无快照数据: " + date);
        }
        return buildByDateResponse(userId, date, snapshots, includeBalance);
    }

    private SnapshotByDateResponse buildByDateResponse(Long userId,
                                                      LocalDate date,
                                                      List<AssetSnapshot> snapshots,
                                                      boolean includeBalance) {
        Map<String, BigDecimal> targetRatios = userConfigService.loadSixCategoryTargetRatios(userId);
        // 1b4pr6b Fix D：getByDate 也应用 user_correct 覆盖
        Map<String, String> userCorrectMap = loadUserCorrectOverrides(userId, date);
        BigDecimal sixTotal = recomputeSixTotal(userId, date, userCorrectMap);
        BigDecimal balanceTotal = includeBalance
                ? recomputeBalanceTotal(userId, date, userCorrectMap)
                : BigDecimal.ZERO;
        LocalDateTime confirmedAt = null;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
        }
        // 1b4pr6b-recovery：与 latest 一致始终输出 7 大类 summary
        List<SnapshotCategorySummary> summaries = buildCanonicalSummaries(
                userId, date, snapshots,
                sixTotal, balanceTotal, userCorrectMap, targetRatios,
                includeBalance, /* includeDetail */ false);
        return SnapshotByDateResponse.builder()
                .snapshotDate(date)
                .snapshotConfirmedAt(confirmedAt)
                .sixCategoriesTotal(sixTotal)
                .balanceFund(balanceTotal)
                .totalAssetWithBalance(sixTotal.add(balanceTotal))
                .categories(summaries)
                .build();
    }

    /**
     * 查询 user 在 [from, to] 的历史快照日期列表（[api-contract.md §3.4](#)）。
     * <p>每个日期默认排除余额类统计 6 大类合计；分页按 page/pageSize。
     */
    public SnapshotHistoryResponse getHistory(Long userId,
                                              LocalDate from,
                                              LocalDate to,
                                              int page,
                                              int pageSize,
                                              boolean includeBalance) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        if (page < 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "page 必须 >= 1");
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "from 不能晚于 to");
        }
        List<LocalDate> dates = Optional.ofNullable(
                assetSnapshotMapper.selectHistoryDates(userId, from, to))
                .orElse(Collections.emptyList());
        long total = dates.size();
        int fromIndex = Math.min((page - 1) * pageSize, dates.size());
        int toIndex = Math.min(fromIndex + pageSize, dates.size());
        List<LocalDate> pageDates = dates.subList(fromIndex, toIndex);
        List<SnapshotHistoryItem> items = pageDates.stream()
                .map(d -> buildHistoryItem(userId, d, includeBalance))
                .collect(Collectors.toList());
        return SnapshotHistoryResponse.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    private SnapshotHistoryItem buildHistoryItem(Long userId, LocalDate date, boolean includeBalance) {
        // 1b4pr6b Fix D：getHistory 也应用 user_correct 覆盖
        Map<String, String> userCorrectMap = loadUserCorrectOverrides(userId, date);
        BigDecimal sixTotal = recomputeSixTotal(userId, date, userCorrectMap);
        BigDecimal balanceTotal = recomputeBalanceTotal(userId, date, userCorrectMap);
        LocalDateTime confirmedAt = null;
        // 1b4pr6b Fix D：categoryCount 反映 effective 后的所有有金额的 category
        Map<String, BigDecimal> byCat = aggregateByEffectiveCategory(userId, date, userCorrectMap, true);
        int categoryCount = byCat.size();
        if (includeBalance) {
            Map<String, BigDecimal> balanceMap = aggregateByEffectiveCategory(userId, date, userCorrectMap, false);
            if (balanceMap.containsKey("余额类")) {
                categoryCount += 1;
            }
        }
        // confirmedAt 仍从 AssetSnapshot 拿（快照元数据，不受 user_correct 影响）
        List<AssetSnapshot> snapshots = Optional.ofNullable(
                assetSnapshotMapper.selectLatestByUserAndDate(userId, date))
                .orElse(Collections.emptyList());
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
        }
        return SnapshotHistoryItem.builder()
                .snapshotDate(date)
                .sixCategoriesTotal(sixTotal)
                .balanceFund(includeBalance ? balanceTotal : BigDecimal.ZERO)
                .categoryCount(categoryCount)
                .confirmedAt(confirmedAt)
                .build();
    }
}

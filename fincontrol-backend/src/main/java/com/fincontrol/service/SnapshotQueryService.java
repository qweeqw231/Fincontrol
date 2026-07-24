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
        // 1b4pr6b Fix D：加载 user_correct 覆盖映射（fundName -> category）
        Map<String, String> userCorrectMap = loadUserCorrectOverrides(userId, snapshotDate);
        // 1b4pr6b Fix D：根据 effective category（override > AI 原猜）重新分桶计算总额
        // 比 AssetSnapshot 预聚合的 totalAmount 更准（因为后者是 AI 猜分类下的聚合）
        BigDecimal sixTotal = recomputeSixTotal(userId, snapshotDate, snapshots);
        BigDecimal balanceTotal = includeBalance
                ? recomputeBalanceTotal(userId, snapshotDate, snapshots)
                : BigDecimal.ZERO;
        LocalDateTime confirmedAt = null;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
        }
        List<SnapshotCategorySummary> summaries = new ArrayList<>();
        for (AssetSnapshot snap : snapshots) {
            String cat = snap.getCategory();
            if (!includeBalance && "余额类".equals(cat)) continue;
            // 1b4pr6b Fix D：如果该 category 已有 user_correct 覆盖，叠加 “从其它 category 切过来的金额”
            // 为保持向后兼容（不破坏 tests），本步骤不修改 total；让前端通过 derivedCategories 呈现聚合表。
            summaries.add(toCategorySummary(userId, snapshotDate, snap, sixTotal, targetRatios, includeDetail));
        }
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
     * 1b.3 补救 R4：按权威金额重算六大类总额（不信任 asset_snapshot 旧比例）。
     * 优先用 asset_raw 在 currentDate 下重算；旧 snapshot 行仅作展示兜底。
     */
    private BigDecimal recomputeSixTotal(Long userId, LocalDate snapshotDate, List<AssetSnapshot> snapshots) {
        BigDecimal sum = BigDecimal.ZERO;
        for (AssetSnapshot snap : snapshots) {
            if (!"余额类".equals(snap.getCategory()) && Boolean.TRUE.equals(snap.getIsLatest())) {
                sum = sum.add(nz(snap.getTotalAmount()));
            }
        }
        // 若 currentDate 下 asset_raw 能算权威金额，则用它覆盖（修复 ratio=0 历史快照）
        List<Map<String, Object>> rows = Optional.ofNullable(
                assetRawQueryMapper.sumSixCategoryAmountsAtDate(userId, snapshotDate))
                .orElse(Collections.emptyList());
        if (!rows.isEmpty()) {
            BigDecimal authoritative = BigDecimal.ZERO;
            for (Map<String, Object> r : rows) {
                authoritative = authoritative.add(toBigDecimal(r.get("total_amount")));
            }
            return authoritative;
        }
        return sum;
    }

    private BigDecimal recomputeBalanceTotal(Long userId, LocalDate snapshotDate, List<AssetSnapshot> snapshots) {
        for (AssetSnapshot snap : snapshots) {
            if ("余额类".equals(snap.getCategory()) && Boolean.TRUE.equals(snap.getIsLatest())) {
                return nz(snap.getTotalAmount());
            }
        }
        // 兜底：从 raw 行汇总
        List<AssetRaw> rows = Optional.ofNullable(
                assetRawQueryMapper.selectCurrentBalanceByUser(userId, snapshotDate))
                .orElse(Collections.emptyList());
        BigDecimal sum = BigDecimal.ZERO;
        for (AssetRaw r : rows) sum = sum.add(nz(r.getAmount()));
        return sum;
    }

    private SnapshotCategorySummary toCategorySummary(Long userId,
                                                     LocalDate snapshotDate,
                                                     AssetSnapshot snap,
                                                     BigDecimal sixTotal,
                                                     Map<String, BigDecimal> targetRatios,
                                                     boolean includeDetail) {
        String category = snap.getCategory();
        BigDecimal total = nz(snap.getTotalAmount());
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
        Integer fundCount = assetRawMapper.countFundsByUserAndDateAndCategory(
                userId, snapshotDate, category);
        if (fundCount == null) {
            fundCount = 0;
        }
        List<SnapshotFundDetail> funds = includeDetail
                ? toFundDetails(userId, snapshotDate, category)
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
        BigDecimal sixTotal = recomputeSixTotal(userId, date, snapshots);
        BigDecimal balanceTotal = includeBalance
                ? recomputeBalanceTotal(userId, date, snapshots)
                : BigDecimal.ZERO;
        LocalDateTime confirmedAt = null;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
        }
        List<SnapshotCategorySummary> summaries = new ArrayList<>();
        for (AssetSnapshot snap : snapshots) {
            String cat = snap.getCategory();
            if (!includeBalance && "余额类".equals(cat)) continue;
            summaries.add(toCategorySummary(userId, date, snap, sixTotal, targetRatios, false));
        }
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
        List<AssetSnapshot> snapshots = Optional.ofNullable(
                assetSnapshotMapper.selectLatestByUserAndDate(userId, date))
                .orElse(Collections.emptyList());
        BigDecimal sixTotal = recomputeSixTotal(userId, date, snapshots);
        BigDecimal balanceTotal = recomputeBalanceTotal(userId, date, snapshots);
        LocalDateTime confirmedAt = null;
        int categoryCount = 0;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
            boolean isBalance = "余额类".equals(snap.getCategory());
            if (isBalance) {
                if (!includeBalance) continue;
            }
            categoryCount++;
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

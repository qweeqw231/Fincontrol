package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.snapshot.SnapshotByDateResponse;
import com.fincontrol.dto.snapshot.SnapshotCategorySummary;
import com.fincontrol.dto.snapshot.SnapshotFundDetail;
import com.fincontrol.dto.snapshot.SnapshotHistoryItem;
import com.fincontrol.dto.snapshot.SnapshotHistoryResponse;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.SnapshotMetaMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 1a.4 快照查询服务（[api-contract.md §3.1/§3.2](#)）。
 *
 * <p>latest 与 latest/detail 共用同一查询入口；只读，不写库。
 */
@Service
public class SnapshotQueryService {

    /** 6 大类合计除以自身的归一化基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetSnapshotMapper assetSnapshotMapper;
    private final AssetRawMapper assetRawMapper;
    private final SnapshotMetaMapper snapshotMetaMapper; // 1b.3.4 决策 27

    public SnapshotQueryService(AssetSnapshotMapper assetSnapshotMapper,
                               AssetRawMapper assetRawMapper,
                               SnapshotMetaMapper snapshotMetaMapper) {
        this.assetSnapshotMapper = assetSnapshotMapper;
        this.assetRawMapper = assetRawMapper;
        this.snapshotMetaMapper = snapshotMetaMapper;
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
        // （替代原 MAX(snapshot_date) WHERE is_latest=true 逻辑）
        LocalDate latestDate = Optional.ofNullable(snapshotMetaMapper.selectCurrentDateByUser(userId))
                .orElse(null);
        if (latestDate == null) {
            return null;
        }
        // 2) 拉取该 user/date 全部 is_latest=true 的 snapshot 行
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
        // 3) 组装 categories（余额类根据 includeBalance 决定是否返回）
        List<SnapshotCategorySummary> summaries = new ArrayList<>();
        BigDecimal sixTotal = BigDecimal.ZERO;
        BigDecimal balanceTotal = BigDecimal.ZERO;
        LocalDateTime confirmedAt = null;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
            boolean isBalance = "余额类".equals(snap.getCategory());
            if (isBalance) {
                if (!includeBalance) {
                    continue;
                }
                balanceTotal = nz(snap.getTotalAmount());
            } else {
                sixTotal = sixTotal.add(nz(snap.getTotalAmount()));
            }
            if (isBalance && !includeBalance) {
                continue;
            }
            summaries.add(toCategorySummary(userId, snapshotDate, snap, includeDetail));
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

    private SnapshotCategorySummary toCategorySummary(Long userId,
                                                     LocalDate snapshotDate,
                                                     AssetSnapshot snap,
                                                     boolean includeDetail) {
        BigDecimal total = nz(snap.getTotalAmount());
        BigDecimal actualRatio = snap.getActualRatio() != null
                ? snap.getActualRatio()
                : BigDecimal.ZERO;
        BigDecimal targetRatio = snap.getTargetRatio() != null
                ? snap.getTargetRatio()
                : BigDecimal.ZERO;
        BigDecimal deviation = actualRatio.subtract(targetRatio);
        Integer fundCount = assetRawMapper.countFundsByUserAndDateAndCategory(
                userId, snapshotDate, snap.getCategory());
        if (fundCount == null) {
            fundCount = 0;
        }
        List<SnapshotFundDetail> funds = includeDetail
                ? toFundDetails(userId, snapshotDate, snap.getCategory())
                : null;
        return SnapshotCategorySummary.builder()
                .categoryName(snap.getCategory())
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
        List<SnapshotCategorySummary> summaries = new ArrayList<>();
        BigDecimal sixTotal = BigDecimal.ZERO;
        BigDecimal balanceTotal = BigDecimal.ZERO;
        LocalDateTime confirmedAt = null;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
            boolean isBalance = "余额类".equals(snap.getCategory());
            if (isBalance) {
                if (!includeBalance) {
                    continue;
                }
                balanceTotal = nz(snap.getTotalAmount());
            } else {
                sixTotal = sixTotal.add(nz(snap.getTotalAmount()));
            }
            if (isBalance && !includeBalance) {
                continue;
            }
            summaries.add(toCategorySummary(userId, date, snap, false));
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
        BigDecimal sixTotal = BigDecimal.ZERO;
        BigDecimal balanceTotal = BigDecimal.ZERO;
        LocalDateTime confirmedAt = null;
        int categoryCount = 0;
        for (AssetSnapshot snap : snapshots) {
            if (snap.getUpdatedAt() != null && (confirmedAt == null || snap.getUpdatedAt().isAfter(confirmedAt))) {
                confirmedAt = snap.getUpdatedAt();
            }
            categoryCount++;
            boolean isBalance = "余额类".equals(snap.getCategory());
            if (isBalance) {
                if (!includeBalance) {
                    categoryCount--; // 未计入响应 categoryCount
                    continue;
                }
                balanceTotal = nz(snap.getTotalAmount());
            } else {
                sixTotal = sixTotal.add(nz(snap.getTotalAmount()));
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

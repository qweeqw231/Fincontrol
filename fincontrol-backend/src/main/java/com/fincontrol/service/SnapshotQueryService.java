package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.snapshot.SnapshotCategorySummary;
import com.fincontrol.dto.snapshot.SnapshotFundDetail;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
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

    public SnapshotQueryService(AssetSnapshotMapper assetSnapshotMapper,
                               AssetRawMapper assetRawMapper) {
        this.assetSnapshotMapper = assetSnapshotMapper;
        this.assetRawMapper = assetRawMapper;
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
        // 1) 找最新日期：取该 user 下 is_latest=true 的最大 snapshot_date
        LocalDate latestDate = Optional.ofNullable(assetSnapshotMapper.selectLatestSnapshotDate(userId))
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
            details.add(SnapshotFundDetail.builder()
                    .fundName(row.getFundName())
                    .amount(nz(row.getAmount()))
                    .profit(nz(row.getProfit()))
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
}

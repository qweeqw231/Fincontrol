package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.asset.AssetBalanceItem;
import com.fincontrol.dto.asset.AssetBalanceResponse;
import com.fincontrol.dto.asset.CumulativeReturnResponse;
import com.fincontrol.dto.asset.OperationRecentItem;
import com.fincontrol.dto.asset.OperationsRecentResponse;

import com.fincontrol.dto.screenshot.ParseLogItem;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.mapper.AssetRawMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 1a.4 首页辅助服务（[api-contract.md §9.1 / §9.2 / §9.3](#)）。
 *
 * <p>只读；不写库。
 */
@Service
public class AssetQueryService {

    private final AssetRawMapper assetRawMapper;
    private final ParseLogQueryService parseLogQueryService;

    public AssetQueryService(AssetRawMapper assetRawMapper,
                             ParseLogQueryService parseLogQueryService) {
        this.assetRawMapper = assetRawMapper;
        this.parseLogQueryService = parseLogQueryService;
    }

    // ========================================================================
    // A4-S06 余额汇总
    // ========================================================================

    /**
     * 余额类卡片数据（[api-contract.md §9.1](#)）。
     * <p>只统计 {@code category='余额类' AND is_latest=true}；空数据返回 total=0、items=[]。
     */
    public AssetBalanceResponse getBalance(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        List<AssetRaw> rows = Optional.ofNullable(assetRawMapper.selectBalanceByUser(userId))
                .orElse(Collections.emptyList());
        List<AssetBalanceItem> items = new ArrayList<>(rows.size());
        BigDecimal total = BigDecimal.ZERO;
        for (AssetRaw row : rows) {
            if (Boolean.FALSE.equals(row.getIsLatest())) {
                continue;
            }
            BigDecimal amount = nz(row.getAmount());
            total = total.add(amount);
            BigDecimal holding = nz(row.getHoldingProfit() != null ? row.getHoldingProfit() : row.getProfit());
            BigDecimal cumulative = nz(row.getCumulativeProfit() != null ? row.getCumulativeProfit() : holding);
            items.add(AssetBalanceItem.builder()
                    .fundName(row.getFundName())
                    .amount(amount)
                    .profit(holding)
                    .holdingProfit(holding)
                    .cumulativeProfit(cumulative)
                    .category(row.getCategory())
                    .build());
        }
        return AssetBalanceResponse.builder()
                .balanceFundTotal(total)
                .items(items)
                .snapshotDate(items.isEmpty() ? null : rows.get(0).getSnapshotDate())
                .build();
    }

    // ========================================================================
    // A4-S07 最近解析活动
    // ========================================================================

    /**
     * 最近解析活动（[api-contract.md §9.2](#)）。
     * <p>复用 {@link ParseLogQueryService}；Phase 1 operationType 固定 screenshot_parse。
     */
    public OperationsRecentResponse getRecentOperations(Long userId, int limit) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<ParseLogItem> logs = parseLogQueryService.listLatest(userId, safeLimit);
        List<OperationRecentItem> items = new ArrayList<>(logs.size());
        for (ParseLogItem log : logs) {
            String status = log.getStatus() == null ? "" : log.getStatus();
            String summary = "parse_failed".equals(status)
                    ? "截图解析失败"
                    : "解析 " + log.getFundCount() + " 只基金";
            items.add(OperationRecentItem.builder()
                    .operationType("screenshot_parse")
                    .operationDate(log.getCreatedAt())
                    .summary(summary)
                    .snapshotDate(log.getSnapshotDate())
                    .build());
        }
        return OperationsRecentResponse.builder().items(items).build();
    }

    // ========================================================================
    // 1b.2 A4-S08 累计收益率（决策 4 v2 / 口径 A / 2026-07-22）
    // ========================================================================

    /**
     * 累计收益率（[api-contract.md §9.3](#) / 决策 4 v2 / 2026-07-22）。
     * <p>算法：{@code totalCumulativeProfit / totalAmount}，分子分母都包含余额类（口径 A / 全口径）。
     * <p>依赖 {@link AssetRawMapper#sumCumProfitAndAmountByUser} 查 user 全部 {@code is_latest=1} 行的 Σ。
     * <p>totalAmount=0 时 returnRate=0（避免除零）。
     * <p>Phase 3 升级为 Modified Dietz / XIRR 时，{@link AssetQueryService#getCumulativeReturn} 改为分支计算，
     * 且算法标识从 {@code phase1_simple} 改为 {@code phase3_dietz} / {@code phase3_xirr}。
     */
    public CumulativeReturnResponse getCumulativeReturn(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        java.util.Map<String, Object> row = assetRawMapper.sumCumProfitAndAmountByUser(userId);
        BigDecimal totalCum = row == null || row.get("total_cumulative_profit") == null
                ? BigDecimal.ZERO
                : new BigDecimal(row.get("total_cumulative_profit").toString());
        BigDecimal totalAmt = row == null || row.get("total_amount") == null
                ? BigDecimal.ZERO
                : new BigDecimal(row.get("total_amount").toString());
        BigDecimal returnRate = totalAmt.signum() == 0
                ? BigDecimal.ZERO
                : totalCum.divide(totalAmt, 6, java.math.RoundingMode.HALF_UP);
        return CumulativeReturnResponse.builder()
                .available(true)
                .algorithm("phase1_simple")
                .totalCumulativeProfit(totalCum)
                .totalAmount(totalAmt)
                .returnRate(returnRate)
                .message(null)
                .build();
    }


    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

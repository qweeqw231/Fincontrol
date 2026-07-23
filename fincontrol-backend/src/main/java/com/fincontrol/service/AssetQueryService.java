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
import com.fincontrol.mapper.AssetRawQueryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 1a.4 首页辅助服务（[api-contract.md §9.1 / §9.2 / §9.3](#)）。
 * <p>1b.3 补救 R3：余额 / 收益 / 基金数 全部绑定 {@code snapshot_meta.is_current} 对应日期。
 * <p>累计/持有公式沿用 1b.2 决策 4 v2 / 决策 25 v2，本轮仅修正输入行范围和目标比例/实际比例。
 */
@Service
public class AssetQueryService {

    private static final Logger log = LoggerFactory.getLogger(AssetQueryService.class);

    private final AssetRawMapper assetRawMapper;
    private final AssetRawQueryMapper assetRawQueryMapper;
    private final ParseLogQueryService parseLogQueryService;
    private final CurrentSnapshotContext currentSnapshotContext;

    public AssetQueryService(AssetRawMapper assetRawMapper,
                             AssetRawQueryMapper assetRawQueryMapper,
                             ParseLogQueryService parseLogQueryService,
                             CurrentSnapshotContext currentSnapshotContext) {
        this.assetRawMapper = assetRawMapper;
        this.assetRawQueryMapper = assetRawQueryMapper;
        this.parseLogQueryService = parseLogQueryService;
        this.currentSnapshotContext = currentSnapshotContext;
    }

    // ========================================================================
    // 1b.3 补救 R3：余额汇总按 currentDate 限定
    // ========================================================================

    /**
     * 余额类卡片数据（[api-contract.md §9.1](#)）。
     * <p>1b.3 行为：仅汇总 {@code snapshot_meta.is_current} 对应日期的余额类 is_latest=1 行。
     * <p>无任何快照时 total=0、items=[]。
     */
    public AssetBalanceResponse getBalance(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        LocalDate currentDate = currentSnapshotContext.resolveCurrentDate(userId);
        if (currentDate == null) {
            return AssetBalanceResponse.builder()
                    .balanceFundTotal(BigDecimal.ZERO)
                    .items(Collections.emptyList())
                    .snapshotDate(null)
                    .build();
        }
        List<AssetRaw> rows = Optional.ofNullable(
                assetRawQueryMapper.selectCurrentBalanceByUser(userId, currentDate))
                .orElse(Collections.emptyList());
        List<AssetBalanceItem> items = new ArrayList<>(rows.size());
        BigDecimal total = BigDecimal.ZERO;
        for (AssetRaw row : rows) {
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
                .snapshotDate(currentDate)
                .build();
    }

    // ========================================================================
    // A4-S07 最近解析活动
    // ========================================================================

    /**
     * 最近解析活动（[api-contract.md §9.2](#)）。
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
            int count = log.getFundCount();
            String summary;
            if ("parse_failed".equals(status) || "parse_unknown".equals(status)) {
                // P5-2a 增强：无法恢复结构时显示原因
                String pe = log.getParseError();
                summary = (pe == null || pe.isBlank()) ? "截图解析失败（结构不可恢复）" : ("解析失败：" + pe);
            } else if (count <= 0) {
                summary = "基金数未知";
            } else {
                summary = "解析 " + count + " 只基金";
            }
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
    // 1b.3 补救 R3：累计/持有/基金数 全部按 currentDate 限定
    // ========================================================================

    /**
     * 累计 + 持有 收益（[api-contract.md §9.3](#) / 决策 4 v2 / 决策 25 v2 / 2026-07-22）。
     * <p>1b.3：分子分母都来自 currentDate；currentDate=null 时直接返回不可用，避免 MAX(date) 兜底回到跨日聚合。
     */
    public CumulativeReturnResponse getCumulativeReturn(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        LocalDate currentDate = currentSnapshotContext.resolveCurrentDate(userId);
        if (currentDate == null) {
            return CumulativeReturnResponse.builder()
                    .available(false)
                    .algorithm("phase1_simple")
                    .message("尚未上传资产快照")
                    .build();
        }
        Map<String, Object> row = assetRawQueryMapper.sumReturnFieldsAtDate(userId, currentDate);

        BigDecimal totalCum = toBigDecimal(row, "total_cumulative_profit");
        BigDecimal rawHolding = toBigDecimal(row, "total_holding_profit");
        BigDecimal totalAmt = toBigDecimal(row, "total_amount");
        BigDecimal returnRate = totalAmt.signum() == 0
                ? BigDecimal.ZERO
                : totalCum.divide(totalAmt, 6, java.math.RoundingMode.HALF_UP);

        // 决策 25 v3：余额宝 fallback（限定到 currentDate）
        BalanceAdjustment adj = computeBalanceAdjustmentAtDate(userId, currentDate);
        BigDecimal totalHold = rawHolding.add(adj.amount());
        BigDecimal holdingReturnRate = totalAmt.signum() == 0
                ? BigDecimal.ZERO
                : totalHold.divide(totalAmt, 6, java.math.RoundingMode.HALF_UP);

        Integer fundCount = row == null || row.get("fund_count") == null
                ? null : ((Number) row.get("fund_count")).intValue();

        return CumulativeReturnResponse.builder()
                .available(true)
                .algorithm("phase1_simple")
                .totalCumulativeProfit(totalCum)
                .rawHoldingProfit(rawHolding)
                .balanceFundAdjustment(adj.amount())
                .totalHoldingProfit(totalHold)
                .totalAmount(totalAmt)
                .returnRate(returnRate)
                .holdingReturnRate(holdingReturnRate)
                .balanceFundStatus(adj.status())
                .snapshotDate(currentDate.toString())
                .fundCount(fundCount)
                .message(null)
                .build();
    }

    private BalanceAdjustment computeBalanceAdjustmentAtDate(Long userId, LocalDate currentDate) {
        List<AssetRaw> balanceRows = Optional.ofNullable(
                assetRawQueryMapper.selectCurrentBalanceByUser(userId, currentDate))
                .orElse(Collections.emptyList());
        AssetRaw baoBao = balanceRows.stream()
                .filter(r -> r.getAmount() != null && r.getAmount().signum() > 0)
                .findFirst()
                .orElse(null);
        if (baoBao == null) {
            return new BalanceAdjustment(BigDecimal.ZERO, "normal");
        }
        BigDecimal holding = baoBao.getHoldingProfit();
        BigDecimal cumulative = baoBao.getCumulativeProfit();
        if (holding == null && cumulative != null) {
            return new BalanceAdjustment(cumulative, "included");
        }
        if (holding == null && cumulative == null) {
            return new BalanceAdjustment(BigDecimal.ZERO, "excluded_unknown");
        }
        return new BalanceAdjustment(BigDecimal.ZERO, "normal");
    }

    /** 内部值对象：余额宝校正值 + 状态 */
    private static class BalanceAdjustment {
        private final BigDecimal amount;
        private final String status;
        BalanceAdjustment(BigDecimal amount, String status) {
            this.amount = amount == null ? BigDecimal.ZERO : amount;
            this.status = status == null ? "normal" : status;
        }
        BigDecimal amount() { return amount; }
        String status() { return status; }
    }

    private static BigDecimal toBigDecimal(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) return BigDecimal.ZERO;
        Object v = row.get(key);
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

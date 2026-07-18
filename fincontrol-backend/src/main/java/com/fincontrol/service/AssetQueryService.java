package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.asset.AssetBalanceItem;
import com.fincontrol.dto.asset.AssetBalanceResponse;
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
    // A4-S08 累计收益率占位
    // ========================================================================

    /**
     * 累计收益率占位（[api-contract.md §9.3](#)）。
     * <p>Phase 1 固定返回 {@code available:false}；Phase 3 再实现。
     */
    public CumulativeReturnPlaceholder getCumulativeReturnPlaceholder() {
        return CumulativeReturnPlaceholder.PHASE1;
    }

    /** 累计收益率占位响应体（无 message 字段、遵循 §9.3） */
    public record CumulativeReturnPlaceholder(String available, String message) {
        public static final CumulativeReturnPlaceholder PHASE1 =
                new CumulativeReturnPlaceholder("false", "累计收益率功能将在 Phase 3 上线");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

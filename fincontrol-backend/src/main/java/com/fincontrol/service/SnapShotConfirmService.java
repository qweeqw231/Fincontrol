package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import com.fincontrol.dto.snapshot.SnapshotConfirmRequest;
import com.fincontrol.dto.snapshot.SnapshotConfirmResult;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.entity.FundCategoryMap;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.DedupEngine.DedupInput;
import com.fincontrol.service.DedupEngine.DedupResult;
import com.fincontrol.service.DedupEngine.DedupWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 1a.3 快照确认业务（[docs/phase-1/designs/1a3-dedup-strategy.md §4.3](#)）。
 *
 * <p>{@code @Transactional} 三表写入 + DedupEngine 5 维 dedup + 镜像校验。
 * 任一异常触发全局回滚。
 *
 * <p>写库顺序：
 * <ol>
 *   <li>{@link AssetRawMapper#insert}  每只 fund 一行</li>
 *   <li>{@link AssetSnapshotMapper#upsertByCategory}  每 category 一行</li>
 *   <li>{@link FundCategoryMapMapper#upsertByFundName}  每只 fund 一行</li>
 *   <li>镜像校验：fund 集合 + category 总额 + is_latest 唯一</li>
 * </ol>
 */
@Service
public class SnapShotConfirmService {

    private static final Logger log = LoggerFactory.getLogger(SnapShotConfirmService.class);

    /** 1a.8 撤销窗口（[P0-3.2]，10s） */
    private static final long ROLLBACK_WINDOW_SECONDS = 10L;

    private final AssetRawMapper assetRawMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;
    private final FundCategoryMapMapper fundCategoryMapMapper;
    private final DedupEngine dedupEngine;

    public SnapShotConfirmService(AssetRawMapper assetRawMapper,
                                 AssetSnapshotMapper assetSnapshotMapper,
                                 FundCategoryMapMapper fundCategoryMapMapper,
                                 DedupEngine dedupEngine) {
        this.assetRawMapper = assetRawMapper;
        this.assetSnapshotMapper = assetSnapshotMapper;
        this.fundCategoryMapMapper = fundCategoryMapMapper;
        this.dedupEngine = dedupEngine;
    }

    /**
     * 1a.3 公共入口（POST /api/snapshot/confirm）。
     *
     * <p>Step 0：dedup 阶段（不写库） → 维度 A/B/C/D/E 全部 + 冲突检测 + 警告收集。
     * <p>Step 1：若 OVERWRITE_REQUIRED 警告 + 未 confirmedOverwrite → 返 warnings 不写库。
     * <p>Step 2：写 asset_raw → asset_snapshot → fund_category_map。
     * <p>Step 3：镜像校验（事务内，失败则 @Transactional 自动回滚）。
     * <p>Step 4：构造 SnapshotConfirmResult（含 1a.8 撤销钩子）。
     */
    @Transactional
    public SnapshotConfirmResult confirm(SnapshotConfirmRequest req) {
        validateRequest(req);

        // ============ Step 0: dedup 阶段（不写库） ============
        DedupResult dedup = dedupEngine.deduplicate(new DedupInput(
                req.getParsedAssets(),
                fundCategoryMapMapper.selectFundNamesByUserAndSnapshotDate(
                        req.getUserId(), req.getSnapshotDate()),
                req.getSnapshotDate(),
                Boolean.TRUE.equals(req.getConfirmedOverwrite())
        ));
        log.info("dedup result: input={} merged={} dropped={} warnings={}",
                dedup.report().inputRecordCount(),
                dedup.report().mergedRecordCount(),
                dedup.report().droppedCount(),
                dedup.report().warnings().size());

        // ============ Step 1: 警告检查（OVERWRITE_REQUIRED + 未确认） ============
        List<String> topLevelWarnings = new ArrayList<>();
        for (DedupWarning w : dedup.report().warnings()) {
            if ("OVERWRITE_REQUIRED".equals(w.code())
                    && !Boolean.TRUE.equals(req.getConfirmedOverwrite())) {
                topLevelWarnings.add(w.message());
            }
        }
        if (!topLevelWarnings.isEmpty()) {
            // 用户未确认 overwrite → 返 warnings 不写库
            return SnapshotConfirmResult.builder()
                    .assetRawInserted(0)
                    .assetSnapshotUpserted(0)
                    .dedupReport(dedup.report())
                    .warnings(topLevelWarnings)
                    .rollbackAvailable(false)
                    .build();
        }

        // ============ Step 2: 写三表 ============
        int assetRawInserted = writeAssetRaw(req, dedup);
        int assetSnapshotUpserted = writeAssetSnapshot(req, dedup);
        int fundMapUpserted = writeFundCategoryMap(req, dedup);

        // ============ Step 3: 镜像校验（事务内，失败则回滚） ============
        verifyMirror(req, dedup);

        log.info("1a.3 confirm done: raw={} snapshot={} map={}",
                assetRawInserted, assetSnapshotUpserted, fundMapUpserted);

        // ============ Step 4: 构造响应 + 1a.8 撤销钩子 ============
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(ROLLBACK_WINDOW_SECONDS);
        return SnapshotConfirmResult.builder()
                .assetRawInserted(assetRawInserted)
                .assetSnapshotUpserted(assetSnapshotUpserted)
                .dedupReport(dedup.report())
                .warnings(topLevelWarnings.isEmpty() ? List.of() : topLevelWarnings)
                .rollbackAvailable(true)
                .rollbackDeadline(deadline)
                .build();
    }

    // ========================================================================
    // 验证
    // ========================================================================

    private void validateRequest(SnapshotConfirmRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "请求体为空");
        }
        if (req.getSnapshotDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE, "snapshotDate 必填");
        }
        if (req.getParsedAssets() == null || req.getParsedAssets().isEmpty()) {
            throw new BusinessException(ErrorCode.VISION_ZERO_FUNDS, "parsedAssets 必填至少 1 张图");
        }
        if (req.getUserId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        // [P0-1.5] 日期校验：AI 识别日期与系统当前日相差 > 7 天
        LocalDateTime now = LocalDateTime.now();
        long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                req.getSnapshotDate(),
                now.toLocalDate()));
        if (daysDiff > 7) {
            throw new BusinessException(
                    ErrorCode.INVALID_SNAPSHOT_DATE,
                    "snapshot_date " + req.getSnapshotDate() + " 与系统当前日相差 " + daysDiff + " 天（>7 天拒绝）"
            );
        }
    }

    // ========================================================================
    // 写库
    // ========================================================================

    private int writeAssetRaw(SnapshotConfirmRequest req, DedupResult dedup) {
        // 决策 27 (Bug 1 修复): 写新批次前先把 (user_id, snapshot_date) 旧行 is_latest 翻 false
        assetRawMapper.updateIsLatestBySnapshotDate(req.getUserId(), req.getSnapshotDate());
        int count = 0;
        // 1a.9：从 dedup 结果获取 totalAssetSource（"top" 或 "visible_sum"）；所有行同值
        String totalAssetSource = dedup.merged().getTotalAssetSource() != null
                ? dedup.merged().getTotalAssetSource() : "top";
        for (CategoryBlock cat : dedup.merged().getCategories()) {
            for (FundLine fund : cat.getFunds()) {
                AssetRaw row = new AssetRaw();
                row.setUserId(req.getUserId());
                row.setSnapshotDate(req.getSnapshotDate());
                row.setFundName(fund.getFundName());
                row.setCategory(cat.getCategoryName());
                row.setAmount(fund.getAmount());
                // 1a.10 P2：余额类特例 —— 支付宝「余额类」截图中无「持有收益」列，
                //   即使模型补 0，service 也要清成 null，保证余额类语义干净。
                //   cumulative（如 +1.89）透传保留；profit 兼容列也置 null。
                boolean isBalanceCategory = "余额类".equals(cat.getCategoryName());
                BigDecimal holding;
                BigDecimal cumulative;
                BigDecimal profit;
                if (isBalanceCategory) {
                    holding = null;
                    profit = null;
                    cumulative = fund.getCumulativeProfit();
                } else {
                    // 1a.8.7：profit / holdingProfit / cumulativeProfit 三列同步写
                    holding = fund.getHoldingProfit() != null
                            ? fund.getHoldingProfit()
                            : (fund.getProfit() != null ? fund.getProfit() : BigDecimal.ZERO);
                    cumulative = fund.getCumulativeProfit() != null
                            ? fund.getCumulativeProfit()
                            : holding;
                    profit = holding;
                }
                row.setProfit(profit);
                row.setHoldingProfit(holding);
                row.setCumulativeProfit(cumulative);
                row.setSource("screenshot_manual");
                row.setTotalAssetSource(totalAssetSource);  // 1a.9：denormalized 写入
                row.setIsLatest(true);
                row.setConfirmedAt(LocalDateTime.now());
                assetRawMapper.insert(row);
                count++;
            }
        }
        return count;
    }

    private int writeAssetSnapshot(SnapshotConfirmRequest req, DedupResult dedup) {
        int count = 0;
        // 1a.9：从 dedup 结果获取 totalAssetSource（“top” 或 “visible_sum”）；所有 category 行同值
        String totalAssetSource = dedup.merged().getTotalAssetSource() != null
                ? dedup.merged().getTotalAssetSource() : "top";
        for (CategoryBlock cat : dedup.merged().getCategories()) {
            AssetSnapshot snap = new AssetSnapshot();
            snap.setUserId(req.getUserId());
            snap.setSnapshotDate(req.getSnapshotDate());
            snap.setCategory(cat.getCategoryName());
            snap.setTotalAmount(cat.getCategoryTotal());
            // 1a.3.5 修法 A：null-safe 三个 NOT NULL BigDecimal 字段
            snap.setTargetRatio(BigDecimal.ZERO);
            snap.setActualRatio(cat.getCategoryPercentage() != null
                    ? cat.getCategoryPercentage()
                    : BigDecimal.ZERO);
            // 余额类金额
            if ("余额类".equals(cat.getCategoryName()) && !Boolean.FALSE.equals(req.getIncludeBalance())) {
                snap.setBalanceFund(cat.getCategoryTotal());
            } else {
                snap.setBalanceFund(BigDecimal.ZERO);
            }
            // 1a.9：写入 total_asset_source（与 asset_raw 同值）
            snap.setTotalAssetSource(totalAssetSource);
            snap.setIsLatest(true);
            assetSnapshotMapper.upsertByCategory(snap);
            count++;
        }
        return count;
    }

    /**
     * 1a.8.8 二态写：
     * <ul>
     *   <li>首次 upsert（无现有行）→ source='ai_guess'，last_seen_at=NOW()（由 mapper upsertByFundName 同步写）</li>
     *   <li>已有行 → source='user_correct'，last_seen_at=NOW()（re-confirm 后由 user_correct 覆盖 ai_guess）</li>
     * </ul>
     * <p>这样清仓后再出现：上次已 user_correct → resolver 返回 isUserConfirmed=true → 前端不弹确认窗。
     */
    private int writeFundCategoryMap(SnapshotConfirmRequest req, DedupResult dedup) {
        int count = 0;
        for (CategoryBlock cat : dedup.merged().getCategories()) {
            for (FundLine fund : cat.getFunds()) {
                FundCategoryMap existing = fundCategoryMapMapper.selectByUserAndFundName(
                        req.getUserId(), fund.getFundName());
                FundCategoryMap map = new FundCategoryMap();
                map.setUserId(req.getUserId());
                map.setFundName(fund.getFundName());
                map.setCategory(cat.getCategoryName());
                map.setSource(existing == null ? "ai_guess" : "user_correct");
                map.setConfirmedAt(LocalDateTime.now());
                fundCategoryMapMapper.upsertByFundName(map);  // upsert 同步写 last_seen_at
                count++;
            }
        }
        return count;
    }

    // ========================================================================
    // 镜像校验
    // ========================================================================

    private void verifyMirror(SnapshotConfirmRequest req, DedupResult dedup) {
        // 校验 1: fund_category_map 中的 fund_name 集合 = asset_raw 中的 fund_name 集合
        Set<String> rawFunds = assetRawMapper.selectFundNamesByUserAndDate(
                req.getUserId(), req.getSnapshotDate());
        Set<String> mapFunds = fundCategoryMapMapper.selectFundNamesByUser(req.getUserId()).stream()
                .filter(f -> dedup.merged().getMatchedFunds().contains(f))
                .collect(Collectors.toSet());
        if (!rawFunds.equals(mapFunds)) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "镜像校验失败：asset_raw fund 集合与 fund_category_map 不一致。raw=" + rawFunds + " map=" + mapFunds);
        }

        // 校验 2: 每个 category 的 sum(asset_raw.amount) = asset_snapshot.total_amount
        for (CategoryBlock cat : dedup.merged().getCategories()) {
            BigDecimal rawSum = assetRawMapper.sumAmountByUserAndDateAndCategory(
                    req.getUserId(), req.getSnapshotDate(), cat.getCategoryName());
            BigDecimal snapTotal = cat.getCategoryTotal();
            if (rawSum == null || snapTotal == null ||
                    rawSum.subtract(snapTotal).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "category " + cat.getCategoryName() + " 镜像校验失败：raw=" + rawSum + " snap=" + snapTotal);
            }
        }

        // 校验 3: is_latest 唯一（同 user_id+date+category 应只 1 行 is_latest=true）
        for (CategoryBlock cat : dedup.merged().getCategories()) {
            int latestCount = assetSnapshotMapper.countLatestByUserAndDateAndCategory(
                    req.getUserId(), req.getSnapshotDate(), cat.getCategoryName());
            if (latestCount != 1) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "category " + cat.getCategoryName() + " is_latest != 1（实际 " + latestCount + "）");
            }
        }
    }
}

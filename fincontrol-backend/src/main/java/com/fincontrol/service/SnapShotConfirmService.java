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
import com.fincontrol.entity.SnapshotMeta;
import com.fincontrol.mapper.SnapshotMetaMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 1a.3 快照确认业务（[docs/phase-1/designs/1a3-dedup-strategy.md §4.3](#)）。
 *
 * <p>{@code @Transactional} 三表写入 + DedupEngine 5 维 dedup + 镜像校验。
 * 任一异常触发全局回滚。
 *
 * <p>1b.4-pr3plus 决策 32 修复：
 * <ul>
 *   <li>P7 完整：writeFundCategoryMap 保留 user_correct 的 category，不再被 AI 覆盖</li>
 *   <li>user_correct map 入参：DedupInput 维度 D 冲突时优先采纳</li>
 * </ul>
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
    private final SnapshotMetaMapper snapshotMetaMapper; // 1b.3.2 决策 27
    private final SettingsService settingsService; // PR3plus 决策 30/31：读 max_snapshot_age_days

    public SnapShotConfirmService(AssetRawMapper assetRawMapper,
                                 AssetSnapshotMapper assetSnapshotMapper,
                                 FundCategoryMapMapper fundCategoryMapMapper,
                                 DedupEngine dedupEngine,
                                 SnapshotMetaMapper snapshotMetaMapper,
                                 SettingsService settingsService) {
        this.assetRawMapper = assetRawMapper;
        this.assetSnapshotMapper = assetSnapshotMapper;
        this.fundCategoryMapMapper = fundCategoryMapMapper;
        this.dedupEngine = dedupEngine;
        this.snapshotMetaMapper = snapshotMetaMapper;
        this.settingsService = settingsService;
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
        // 1b.4-pr3plus 决策 32：构建 existingUserCorrectCategories map（fund_name → category）
        // 用途：DedupEngine 维度 D 冲突时，user_correct 优先；未命中保留首次。
        // 性能：当前 N 次单查可接受，因 user_correct 行数通常 < 50；未来可优化为单次 SQL IN 查询。
        Map<String, String> userCorrectMap = new HashMap<>();
        for (String fundName : fundCategoryMapMapper.selectFundNamesByUser(req.getUserId())) {
            FundCategoryMap entry = fundCategoryMapMapper.selectByUserAndFundName(
                    req.getUserId(), fundName);
            if (entry != null && "user_correct".equals(entry.getSource())) {
                userCorrectMap.put(fundName, entry.getCategory());
            }
        }
        DedupResult dedup = dedupEngine.deduplicate(new DedupInput(
                req.getParsedAssets(),
                fundCategoryMapMapper.selectFundNamesByUserAndSnapshotDate(
                        req.getUserId(), req.getSnapshotDate()),
                userCorrectMap,
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
        // 决策 33 D7（R5）：消失-重现机制 - 仅在 isAnchorUpdate 时更新 last_seen / first_missing
        int r5Updated = detectAndUpdateDisappearReappear(req, dedup);
        // 1b.3.2 决策 27：写 snapshot_meta 元数据（per-date is_latest + cross-date is_current）
        int snapshotMetaUpserted = writeSnapshotMeta(req, dedup);

        // ============ Step 3: 镜像校验（事务内，失败则回滚） ============
        verifyMirror(req, dedup);

        log.info("1b.3.2 confirm done: raw={} snapshot={} map={} meta={}",
                assetRawInserted, assetSnapshotUpserted, fundMapUpserted, snapshotMetaUpserted);

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
        // [P0-1.5] 日期校验（PR3plus 决策 30/31）：从 settings 表读 max_snapshot_age_days
        // -1 = 不限制；7/14/30/180 = 限定天数（前端可改）
        // 后端不硬编码，默认从 settings 查，找不到返 7。
        LocalDateTime now = LocalDateTime.now();
        long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                req.getSnapshotDate(),
                now.toLocalDate()));
        int maxAge = settingsService.getMaxSnapshotAgeDays(req.getUserId());
        if (maxAge != -1 && daysDiff > maxAge) {
            throw new BusinessException(
                    ErrorCode.INVALID_SNAPSHOT_DATE,
                    "snapshot_date " + req.getSnapshotDate() + " 与系统当前日相差 " + daysDiff
                            + " 天（>" + maxAge + " 天拒绝，当前限制可在数据管理页点击 [修改历史限制] 调整）"
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
        // 决策 33 D4：与 writeAssetRaw 对称，写新批次前先翻旧 asset_snapshot 行 is_latest=false
        assetSnapshotMapper.updateIsLatestBySnapshotDate(req.getUserId(), req.getSnapshotDate());
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
     * 1b.4-pr3plus 决策 32：P7 修复完整版 — user_correct / user_manual 行不覆写
     *   （保留用户的明确分类 + 保留 source 标记）。
     *   ai_guess 行可被 AI 新结果更新（source 保持 ai_guess，不自动升 user_correct）。
     *   首次入库：source='ai_guess'，category=AI 解析值。
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
                String existingSource = existing == null ? null : existing.getSource();
                String existingCategory = existing == null ? null : existing.getCategory();
                String aiCategory = cat.getCategoryName();
                String finalCategory;
                String finalSource;
                if (existingSource == null) {
                    // 首次入库
                    finalCategory = aiCategory;
                    finalSource = "ai_guess";
                } else if ("ai_guess".equals(existingSource)) {
                    // AI 可更新自己的 guess（但 source 保持 ai_guess，不自动升 user_correct）
                    finalCategory = aiCategory;
                    finalSource = "ai_guess";
                } else {
                    // user_correct / user_manual：保留用户已确认的 category 和 source，
                    // 拒绝 AI 覆盖（否则一次 AI 错误会偷偷"纠正"用户决策）。
                    finalCategory = existingCategory != null ? existingCategory : aiCategory;
                    finalSource = existingSource;
                }
                map.setCategory(finalCategory);
                map.setSource(finalSource);
                map.setConfirmedAt(LocalDateTime.now());
                fundCategoryMapMapper.upsertByFundName(map);  // upsert 同步写 last_seen_at
                count++;
            }
        }
        return count;
    }

    /**
     * 1b.3.2 决策 27：写 snapshot_meta 元数据。
     * <p>每次 confirm 都自动：
     * <ol>
     *   <li>清 (user_id) 全部 is_current=false（确保唯一 current）</li>
     *   <li>UPSERT (user_id, snapshot_date) is_latest=true, is_current=true</li>
     * </ol>
     * <p>注：snapshot_meta 表的 is_latest 与 asset_raw.is_latest 同步，
     * 旧行 is_latest 翻 false 已在 writeAssetRaw 前置（assertAssetRaw mapper 调用）
     * 这里只需维护 snapshot_meta 表本身的最新状态。
     */
    private int writeSnapshotMeta(SnapshotConfirmRequest req, DedupResult dedup) {
        // Step 1：翻 (user_id) 全部 is_current=false（保证唯一 current）
        snapshotMetaMapper.clearCurrentForUser(req.getUserId());

        // Step 2：UPSERT (user_id, snapshot_date) is_latest=true, is_current=true
        SnapshotMeta meta = new SnapshotMeta();
        meta.setUserId(req.getUserId());
        meta.setSnapshotDate(req.getSnapshotDate());
        meta.setConfirmedAt(LocalDateTime.now());
        // is_latest 与 is_current 都为 true（UPSERT 内已写死）
        return snapshotMetaMapper.upsertOnConfirm(meta);
    }

    // ========================================================================
    // 镜像校验
    // ========================================================================

    // ========================================================================
    // 决策 33 D7：消失-重现机制（R5）
    //   锉点 = MAX(last_seen_snapshot_date) for user_id
    //   仅当 snapshotDate >= anchorDate（isAnchorUpdate=true）时执行 forward inference
    //   历史回填（snapshotDate < anchorDate）仅入库，不修改 last_seen / first_missing
    // ========================================================================

    /**
     * 决策 33 D7（R5）：锉点计算 + 消失-重现检测。
     * <p>仅在 {@code snapshotDate >= anchorDate}（isAnchorUpdate）时执行 forward inference：
     * <ul>
     *   <li>本次出现 → 更新 last_seen_snapshot_date</li>
     *   <li>本次出现 + 之前 first_missing 非 NULL → 清 first_missing（重现事件）</li>
     *   <li>本次未出现 + last_seen ≤ snapshotDate + first_missing 为 NULL → 标记 first_missing</li>
     *   <li>历史回填 → 仅入库，锉点不变</li>
     * </ul>
     * <p>返回更新的记录数（调试用）。
     */
    private int detectAndUpdateDisappearReappear(SnapshotConfirmRequest req, DedupResult dedup) {
        LocalDate snapshotDate = req.getSnapshotDate();
        Long userId = req.getUserId();

        LocalDate anchorDate = fundCategoryMapMapper.selectMaxLastSeenSnapshotDate(userId);
        boolean isAnchorUpdate = anchorDate == null || !snapshotDate.isBefore(anchorDate);

        if (!isAnchorUpdate) {
            log.info("决策 33 D7 (R5): 回填 snapshotDate={} < anchorDate={}，跳过锉点更新",
                    snapshotDate, anchorDate);
            return 0;
        }

        Set<String> currentFunds = req.getParsedAssets().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(a -> Optional.ofNullable(a.getCategories()).orElse(Collections.emptyList()).stream())
                .filter(java.util.Objects::nonNull)
                .flatMap(c -> Optional.ofNullable(c.getFunds()).orElse(Collections.emptyList()).stream())
                .filter(java.util.Objects::nonNull)
                .map(FundLine::getFundName)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        List<String> knownFundNames = fundCategoryMapMapper.selectFundNamesByUser(userId);
        int updated = 0;
        int reappeared = 0;
        int newlyDisappeared = 0;

        for (String fundName : knownFundNames) {
            FundCategoryMap rec = fundCategoryMapMapper.selectByUserAndFundName(userId, fundName);
            if (rec == null) continue;

            if (currentFunds.contains(fundName)) {
                // 本次出现 → 更新 last_seen_snapshot_date
                boolean wasFirstMissing = rec.getFirstMissingSnapshotDate() != null;
                rec.setLastSeenSnapshotDate(snapshotDate);
                if (wasFirstMissing) {
                    // 重现事件：清 first_missing_snapshot_date
                    rec.setFirstMissingSnapshotDate(null);
                    reappeared++;
                    log.info("决策 33 D7 (R5): 基金 {} 重现（previous first_missing={}），清 first_missing_snapshot_date",
                            fundName, snapshotDate);
                }
                fundCategoryMapMapper.updateById(rec);
                updated++;
            } else {
                // 本次未出现 → 可能是清仓
                if (rec.getLastSeenSnapshotDate() != null
                        && !rec.getLastSeenSnapshotDate().isAfter(snapshotDate)) {
                    if (rec.getFirstMissingSnapshotDate() == null) {
                        // 第一次发现消失
                        rec.setFirstMissingSnapshotDate(snapshotDate);
                        fundCategoryMapMapper.updateById(rec);
                        updated++;
                        newlyDisappeared++;
                        log.info("决策 33 D7 (R5): 基金 {} 在 snapshotDate={} 第一次发现消失",
                                fundName, snapshotDate);
                    }
                    // 否则 first_missing 已有 → 保持（不要覆盖原始发现日）
                }
            }
        }

        log.info("决策 33 D7 (R5): snapshotDate={} anchorDate={} updated={} reappeared={} newlyDisappeared={}",
                snapshotDate, anchorDate, updated, reappeared, newlyDisappeared);
        return updated;
    }

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

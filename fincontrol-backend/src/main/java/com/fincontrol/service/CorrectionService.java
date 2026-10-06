package com.fincontrol.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.correction.CorrectionConfirmResponse;
import com.fincontrol.dto.correction.CorrectionDefaultsResponse;
import com.fincontrol.dto.correction.MonthlyCalculateRequest;
import com.fincontrol.dto.correction.MonthlyCalculateResponse;
import com.fincontrol.dto.correction.MonthlyConfirmRequest;
import com.fincontrol.dto.correction.MonthlyRecalculateRequest;
import com.fincontrol.dto.correction.MonthlyRecalculateResponse;
import com.fincontrol.dto.correction.QuarterlyCalculateRequest;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse;
import com.fincontrol.dto.correction.QuarterlyConfirmRequest;
import com.fincontrol.dto.correction.QuarterlyDefaultsResponse;
import com.fincontrol.dto.snapshot.SnapshotCategorySummary;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.CorrectionAssetDetail;
import com.fincontrol.entity.CorrectionIteration;
import com.fincontrol.entity.CorrectionParam;
import com.fincontrol.entity.OperationLog;
import com.fincontrol.mapper.CorrectionAssetDetailMapper;
import com.fincontrol.mapper.CorrectionIterationMapper;
import com.fincontrol.mapper.CorrectionParamMapper;
import com.fincontrol.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 2a 校正页编排服务（默认值 / 求解入口 / 确认入库 / 记录列表与详情）。
 *
 * <p>数据来源：当前 {@code is_current} 快照（SnapshotQueryService）+ {@code user_config}；
 * 写入：{@code operation_log} + 3 张明细子表（季度确认走单事务）。
 * <p>遵循契约：确认不修改 asset_raw / asset_snapshot。
 */
@Service
public class CorrectionService {

    private static final Logger log = LoggerFactory.getLogger(CorrectionService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final BigDecimal DEFAULT_U_HIGH = new BigDecimal("560");
    private static final BigDecimal DEFAULT_BUDGET_LIMIT = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("100");
    private static final BigDecimal DEFAULT_ALPHA_INIT = new BigDecimal("0.20");
    private static final BigDecimal DEFAULT_ALPHA_DECAY = new BigDecimal("0.8");

    private final SnapshotQueryService snapshotQueryService;
    private final UserConfigService userConfigService;
    private final CurrentSnapshotContext currentSnapshotContext;
    private final CorrectionSolveService solveService;
    private final OperationLogMapper operationLogMapper;
    private final CorrectionIterationMapper iterationMapper;
    private final CorrectionAssetDetailMapper assetDetailMapper;
    private final CorrectionParamMapper paramMapper;
    private final ObjectMapper json = new ObjectMapper();

    public CorrectionService(SnapshotQueryService snapshotQueryService,
                             UserConfigService userConfigService,
                             CurrentSnapshotContext currentSnapshotContext,
                             CorrectionSolveService solveService,
                             OperationLogMapper operationLogMapper,
                             CorrectionIterationMapper iterationMapper,
                             CorrectionAssetDetailMapper assetDetailMapper,
                             CorrectionParamMapper paramMapper) {
        this.snapshotQueryService = snapshotQueryService;
        this.userConfigService = userConfigService;
        this.currentSnapshotContext = currentSnapshotContext;
        this.solveService = solveService;
        this.operationLogMapper = operationLogMapper;
        this.iterationMapper = iterationMapper;
        this.assetDetailMapper = assetDetailMapper;
        this.paramMapper = paramMapper;
    }

    // ========================================================================
    // 月度台
    // ========================================================================

    /**
     * `GET /api/correction/defaults`：月度台默认值。
     * <p>无当前快照时返回 null（前端展示 Empty 并引导去 /data），不返回全 0 伪默认值。
     */
    public CorrectionDefaultsResponse getMonthlyDefaults(Long userId) {
        LocalDate currentDate = currentSnapshotContext.resolveCurrentDate(userId);
        if (currentDate == null) {
            return null;
        }
        SnapshotLatestResponse latest = snapshotQueryService.getLatest(userId, false, false);
        if (latest == null) {
            return null;
        }
        Map<String, BigDecimal> categories = sixMap(latest);
        Map<String, BigDecimal> targets = userConfigService.loadSixCategoryTargetRatios(userId);
        BigDecimal uHigh = userConfigService.loadDecimal(userId, "high_vol_dca_budget", DEFAULT_U_HIGH);

        BigDecimal det = det(targets);
        CorrectionDefaultsResponse resp = new CorrectionDefaultsResponse();
        resp.setVTotalSixCategories(latest.getSixCategoriesTotal());
        resp.setVMonetary(categories.get("货币类"));
        resp.setVBond(categories.get("固收类"));
        resp.setVHighVol(highVolOf(categories));
        resp.setUHigh(uHigh);
        resp.setUMonetaryDcaTheory(uHigh.multiply(frac(targets.get("货币类"), BigDecimal.ZERO))
                .divide(det, 2, RoundingMode.HALF_UP));
        resp.setUBondDcaTheory(uHigh.multiply(frac(targets.get("固收类"), BigDecimal.ZERO))
                .divide(det, 2, RoundingMode.HALF_UP));
        resp.setTargetRatios(targets);
        resp.setBudgetLimit(userConfigService.loadDecimal(userId, "monthly_budget_limit", DEFAULT_BUDGET_LIMIT));
        resp.setPurchaseThreshold(userConfigService.loadDecimal(userId, "purchase_threshold", DEFAULT_THRESHOLD));
        resp.setSnapshotDate(currentDate.toString());
        resp.setSnapshotNote("六大类合计，不含余额类");
        resp.setCategories(categories);
        return resp;
    }

    /** `POST /api/correction/monthly/calculate`：缺省字段自动从快照/配置补齐。 */
    public MonthlyCalculateResponse calculateMonthly(Long userId, MonthlyCalculateRequest req) {
        boolean needDefaults = req.getVCurr() == null || req.getVMonetary() == null
                || req.getVBond() == null || req.getUHigh() == null
                || req.getPMonetary() == null || req.getPBond() == null
                || req.getPurchaseThreshold() == null || req.getBudgetLimit() == null;
        if (needDefaults) {
            CorrectionDefaultsResponse d = getMonthlyDefaults(userId);
            if (d != null) {
                if (req.getVCurr() == null) req.setVCurr(d.getVTotalSixCategories());
                if (req.getVMonetary() == null) req.setVMonetary(d.getVMonetary());
                if (req.getVBond() == null) req.setVBond(d.getVBond());
                if (req.getUHigh() == null) req.setUHigh(d.getUHigh());
                if (req.getPMonetary() == null) req.setPMonetary(d.getTargetRatios().get("货币类"));
                if (req.getPBond() == null) req.setPBond(d.getTargetRatios().get("固收类"));
                if (req.getPurchaseThreshold() == null) req.setPurchaseThreshold(d.getPurchaseThreshold());
                if (req.getBudgetLimit() == null) req.setBudgetLimit(d.getBudgetLimit());
            }
        }
        if (req.getVCurr() == null || req.getVMonetary() == null || req.getVBond() == null) {
            throw new BusinessException(ErrorCode.INVALID_CORRECTION_PARAM,
                    "缺少当前快照数据，请先在数据管理页上传资产快照");
        }
        return solveService.solveMonthly(req);
    }

    /** `POST /api/correction/monthly/recalculate`：预算上限缺省时读配置。 */
    public MonthlyRecalculateResponse recalculateMonthly(Long userId, MonthlyRecalculateRequest req) {
        if (req.getBudgetLimit() == null) {
            req.setBudgetLimit(userConfigService.loadDecimal(userId, "monthly_budget_limit", DEFAULT_BUDGET_LIMIT));
        }
        return solveService.recalculateMonthly(req);
    }

    /** `POST /api/correction/monthly/confirm`：写入 operation_log（不修改快照三表）。 */
    @Transactional
    public CorrectionConfirmResponse confirmMonthly(Long userId, MonthlyConfirmRequest req) {
        OperationLog op = new OperationLog();
        op.setUserId(userId);
        op.setOperationDate(LocalDateTime.now());
        op.setOperationType("monthly_correction");
        op.setCorrectionMode(req.getCorrectionMode() != null && !req.getCorrectionMode().isBlank()
                ? req.getCorrectionMode() : "zoh_only");
        op.setSnapshotDate(parseDate(req.getSnapshotDate()));
        op.setVCurr(req.getVCurr());
        op.setVMonetary(req.getVMonetary());
        op.setVBond(req.getVBond());
        op.setVHighVol(req.getVHighVol());
        op.setUHigh(req.getUHigh());
        op.setUMonetaryDca(req.getUMonetaryDca());
        op.setUBondDca(req.getUBondDca());
        op.setDeltaMTheory(req.getDeltaMTheory());
        op.setDeltaBTheory(req.getDeltaBTheory());
        op.setDeltaMActual(req.getDeltaMActual());
        op.setDeltaBActual(req.getDeltaBActual());
        op.setRoundingStrategy(req.getRoundingStrategy());
        op.setBudgetLimitUsed(req.getBudgetLimitUsed());
        op.setTotalInvestment(req.getTotalInvestment());
        op.setTriggeredBoundary(req.getTriggeredBoundary() != null && !req.getTriggeredBoundary().isBlank()
                ? req.getTriggeredBoundary()
                : (req.getDeltaMActual() != null && req.getDeltaMActual().signum() > 0 ? "1" : null));
        op.setWarnings(toJsonArray(req.getNotes()));
        op.setTargetRatios(toJson(targetRatiosOf(userId)));
        op.setSource(req.getSource() != null && !req.getSource().isBlank()
                ? req.getSource() : "correction_page");
        op.setConfirmedAt(LocalDateTime.now());
        operationLogMapper.insert(op);
        log.info("2a 月度校正确认入库: userId={} operationLogId={}", userId, op.getId());

        return CorrectionConfirmResponse.builder()
                .operationLogId(op.getId())
                .operationDate(op.getOperationDate().format(DATE_FMT))
                .writtenToOperationLog(true)
                .detailRows(0)
                .build();
    }

    // ========================================================================
    // 季度台（LQR-ZOH 联合校正）
    // ========================================================================

    /** `GET /api/correction/quarterly/defaults`。 */
    public QuarterlyDefaultsResponse getQuarterlyDefaults(Long userId) {
        LocalDate currentDate = currentSnapshotContext.resolveCurrentDate(userId);
        if (currentDate == null) {
            return null;
        }
        SnapshotLatestResponse latest = snapshotQueryService.getLatest(userId, false, false);
        if (latest == null) {
            return null;
        }
        Map<String, BigDecimal> categories = sixMap(latest);
        Map<String, BigDecimal> targets = userConfigService.loadSixCategoryTargetRatios(userId);
        BigDecimal uHigh = userConfigService.loadDecimal(userId, "high_vol_dca_budget", DEFAULT_U_HIGH);
        BigDecimal det = det(targets);

        QuarterlyDefaultsResponse resp = new QuarterlyDefaultsResponse();
        resp.setSnapshotDate(currentDate.toString());
        resp.setVCurr(latest.getSixCategoriesTotal());
        resp.setVHighVol(highVolOf(categories));
        resp.setCategories(categories);
        resp.setTargetRatios(targets);
        resp.setUHighDefault(uHigh);
        resp.setPurchaseThreshold(userConfigService.loadDecimal(userId, "purchase_threshold", DEFAULT_THRESHOLD));
        resp.setAlphaInitDefault(DEFAULT_ALPHA_INIT);
        resp.setAlphaDecayDefault(DEFAULT_ALPHA_DECAY);
        resp.setUMonetaryDcaTheory(uHigh.multiply(frac(targets.get("货币类"), BigDecimal.ZERO))
                .divide(det, 2, RoundingMode.HALF_UP));
        resp.setUBondDcaTheory(uHigh.multiply(frac(targets.get("固收类"), BigDecimal.ZERO))
                .divide(det, 2, RoundingMode.HALF_UP));
        resp.setSnapshotNote("六大类合计，不含余额类");
        return resp;
    }

    /** `POST /api/correction/quarterly/calculate`：缺省字段自动补齐后进入求解。 */
    public QuarterlyCalculateResponse calculateQuarterly(Long userId, QuarterlyCalculateRequest req) {
        boolean needDefaults = req.getVCurr() == null || req.getCategories() == null
                || req.getCategories().isEmpty() || req.getTargetRatios() == null
                || req.getUHigh() == null || req.getPurchaseThreshold() == null;
        if (needDefaults) {
            QuarterlyDefaultsResponse d = getQuarterlyDefaults(userId);
            if (d != null) {
                if (req.getVCurr() == null) req.setVCurr(d.getVCurr());
                if (req.getCategories() == null || req.getCategories().isEmpty()) {
                    req.setCategories(d.getCategories());
                }
                if (req.getTargetRatios() == null || req.getTargetRatios().isEmpty()) {
                    req.setTargetRatios(d.getTargetRatios());
                }
                if (req.getUHigh() == null) req.setUHigh(d.getUHighDefault());
                if (req.getPurchaseThreshold() == null) req.setPurchaseThreshold(d.getPurchaseThreshold());
            }
        }
        if (req.getVCurr() == null || req.getCategories() == null || req.getCategories().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_CORRECTION_PARAM,
                    "缺少当前快照数据，请先在数据管理页上传资产快照");
        }
        if (req.getTargetRatios() == null || req.getTargetRatios().isEmpty()) {
            req.setTargetRatios(userConfigService.loadSixCategoryTargetRatios(userId));
        }
        // M_max 缺省 = 1.8 × S
        boolean mMaxExplicit = req.getMMax() != null;
        BigDecimal mMax = req.getMMax();
        if (mMax == null) {
            if (req.getSurplus() == null) {
                throw new BusinessException(ErrorCode.INVALID_CORRECTION_PARAM,
                        "请提供当月结余 S 或总投入上限 M_max");
            }
            mMax = req.getSurplus().multiply(new BigDecimal("1.8"));
        }

        CorrectionSolveService.QuarterlyInput in = new CorrectionSolveService.QuarterlyInput();
        in.vCurr = req.getVCurr();
        in.categories = req.getCategories();
        in.surplus = req.getSurplus();
        in.mMax = mMax;
        in.mMaxExplicit = mMaxExplicit;
        in.mMaxSource = req.getMMaxSource();
        in.uHigh = req.getUHigh();
        in.uMonetaryDca = req.getUMonetaryDca();
        in.uBondDca = req.getUBondDca();
        in.alphaInit = req.getAlphaInit();
        in.alphaDecay = req.getAlphaDecay();
        in.purchaseThreshold = req.getPurchaseThreshold();
        in.targetRatios = req.getTargetRatios();
        in.mode = req.getMode();
        in.alphaProbes = req.getAlphaProbes();
        in.probeBaseMmax = req.getProbeBaseMmax();
        in.maxIterations = req.getMaxIterations();
        return solveService.solveQuarterly(in);
    }

    /** `POST /api/correction/quarterly/confirm`：单事务写入 4 张表。 */
    @Transactional
    public CorrectionConfirmResponse confirmQuarterly(Long userId, QuarterlyConfirmRequest req) {
        OperationLog op = new OperationLog();
        op.setUserId(userId);
        op.setOperationDate(LocalDateTime.now());
        op.setOperationType("quarterly_correction");
        op.setCorrectionMode(req.getCorrectionMode() != null && !req.getCorrectionMode().isBlank()
                ? req.getCorrectionMode() : "lqr_zoh");
        op.setSnapshotDate(parseDate(req.getSnapshotDate()));
        op.setVCurr(req.getVCurr());
        op.setVMonetary(req.getVMonetary());
        op.setVBond(req.getVBond());
        op.setVHighVol(req.getVHighVol());
        op.setUHigh(req.getUHigh());
        op.setUMonetaryDca(req.getUMonetaryDca());
        op.setUBondDca(req.getUBondDca());
        op.setDeltaMTheory(req.getDeltaMTheory());
        op.setDeltaBTheory(req.getDeltaBTheory());
        op.setDeltaMActual(req.getDeltaMActual());
        op.setDeltaBActual(req.getDeltaBActual());
        op.setRoundingStrategy(req.getRoundingStrategy());
        op.setBudgetLimitUsed(req.getBudgetLimitUsed());
        op.setTotalInvestment(req.getTotalInvestment());
        op.setTriggeredBoundary(req.getTriggeredBoundary());
        op.setWarnings(toJsonArray(req.getNotes()));
        op.setTargetRatios(toJson(targetRatiosOf(userId)));
        op.setSource(req.getSource() != null && !req.getSource().isBlank()
                ? req.getSource() : "correction_page");
        op.setConfirmedAt(LocalDateTime.now());
        operationLogMapper.insert(op);

        int rows = 0;
        if (req.getIterations() != null) {
            int idx = 0;
            for (QuarterlyConfirmRequest.IterationItem it : req.getIterations()) {
                CorrectionIteration row = new CorrectionIteration();
                row.setUserId(userId);
                row.setOperationLogId(op.getId());
                row.setSortOrder(it.getSortOrder() != null ? it.getSortOrder() : idx);
                row.setAlpha(it.getAlpha());
                row.setEHigh(it.getEHigh());
                row.setDeltaM(it.getDeltaM());
                row.setDeltaB(it.getDeltaB());
                row.setZohTriggered(Boolean.TRUE.equals(it.getZohTriggered()));
                row.setTotalInvestment(it.getTotalInvestment());
                row.setOverLimit(it.getOverLimit());
                row.setNote(it.getNote());
                iterationMapper.insert(row);
                rows++;
                idx++;
            }
        }
        if (req.getAssets() != null) {
            int idx = 0;
            for (QuarterlyConfirmRequest.AssetItem it : req.getAssets()) {
                CorrectionAssetDetail row = new CorrectionAssetDetail();
                row.setUserId(userId);
                row.setOperationLogId(op.getId());
                row.setSortOrder(idx);
                row.setPhase(it.getPhase());
                row.setCategory(it.getCategory());
                row.setAmount(it.getAmount());
                row.setRatioActual(it.getRatioActual());
                row.setRatioTarget(it.getRatioTarget());
                row.setDeviation(it.getDeviation());
                row.setDeltaRaw(it.getDeltaRaw());
                row.setDeltaAmount(it.getDeltaAmount());
                row.setNote(it.getNote());
                assetDetailMapper.insert(row);
                rows++;
                idx++;
            }
        }
        if (req.getParams() != null) {
            for (QuarterlyConfirmRequest.ParamItem it : req.getParams()) {
                if (it.getKey() == null || it.getKey().isBlank()) continue;
                CorrectionParam row = new CorrectionParam();
                row.setUserId(userId);
                row.setOperationLogId(op.getId());
                row.setParamKey(it.getKey());
                row.setNumValue(it.getNumValue());
                row.setTextValue(it.getTextValue());
                paramMapper.insert(row);
                rows++;
            }
        }
        log.info("2a 季度联合校正确认入库: userId={} operationLogId={} detailRows={}",
                userId, op.getId(), rows);

        return CorrectionConfirmResponse.builder()
                .operationLogId(op.getId())
                .operationDate(op.getOperationDate().format(DATE_FMT))
                .writtenToOperationLog(true)
                .detailRows(rows)
                .build();
    }

    // ========================================================================
    // 记录列表与详情
    // ========================================================================

    /** `GET /api/correction/operations`：全部校正与操作记录（含明细摘要）。 */
    public Map<String, Object> listOperations(Long userId) {
        List<OperationLog> rows = operationLogMapper.selectAllByUser(userId);
        List<CorrectionParam> allParams = paramMapper.selectByUser(userId);
        Map<Long, Map<String, CorrectionParam>> paramByOp = new HashMap<>();
        for (CorrectionParam p : allParams) {
            paramByOp.computeIfAbsent(p.getOperationLogId(), k -> new LinkedHashMap<>())
                    .put(p.getParamKey(), p);
        }
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (OperationLog op : rows) {
            Map<String, Object> item = operationSummary(op);
            Map<String, CorrectionParam> params = paramByOp.getOrDefault(op.getId(), Collections.emptyMap());
            item.put("hasDetail", !params.isEmpty());
            CorrectionParam icDrr = params.get("icDrrPct");
            item.put("icDrrPct", icDrr == null ? null : scale(icDrr.getNumValue(), 2));
            CorrectionParam anchor = params.get("anchor");
            item.put("anchor", anchor == null ? null : anchor.getTextValue());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        return result;
    }

    /** `GET /api/correction/operations/{id}`：单条记录完整明细（回放）。 */
    public Map<String, Object> getOperationDetail(Long userId, Long id) {
        OperationLog op = operationLogMapper.selectById(id);
        if (op == null || !Objects.equals(op.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_CORRECTION_PARAM, "校正记录不存在或无权访问");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", operationSummary(op));

        List<Map<String, Object>> iterations = new ArrayList<>();
        for (CorrectionIteration it : iterationMapper.selectByOperationLog(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sortOrder", it.getSortOrder());
            m.put("alpha", it.getAlpha());
            m.put("eHigh", scale(it.getEHigh(), 2));
            m.put("deltaM", scale(it.getDeltaM(), 2));
            m.put("deltaB", scale(it.getDeltaB(), 2));
            m.put("zohTriggered", it.getZohTriggered());
            m.put("totalInvestment", scale(it.getTotalInvestment(), 2));
            m.put("overLimit", scale(it.getOverLimit(), 2));
            m.put("note", it.getNote());
            iterations.add(m);
        }
        result.put("iterations", iterations);

        List<Map<String, Object>> assets = new ArrayList<>();
        for (CorrectionAssetDetail a : assetDetailMapper.selectByOperationLog(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phase", a.getPhase());
            m.put("category", a.getCategory());
            m.put("amount", scale(a.getAmount(), 2));
            m.put("ratioActual", scale(a.getRatioActual(), 2));
            m.put("ratioTarget", scale(a.getRatioTarget(), 2));
            m.put("deviation", scale(a.getDeviation(), 2));
            m.put("deltaRaw", scale(a.getDeltaRaw(), 2));
            m.put("deltaAmount", scale(a.getDeltaAmount(), 2));
            m.put("note", a.getNote());
            assets.add(m);
        }
        result.put("assets", assets);

        Map<String, Object> params = new LinkedHashMap<>();
        for (CorrectionParam p : paramMapper.selectByOperationLog(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numValue", p.getNumValue());
            m.put("textValue", p.getTextValue());
            params.put(p.getParamKey(), m);
        }
        result.put("params", params);
        return result;
    }

    private Map<String, Object> operationSummary(OperationLog op) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", op.getId());
        item.put("operationDate", op.getOperationDate() == null ? null
                : op.getOperationDate().format(DATETIME_FMT));
        item.put("operationType", op.getOperationType());
        item.put("correctionMode", op.getCorrectionMode());
        item.put("snapshotDate", op.getSnapshotDate() == null ? null : op.getSnapshotDate().toString());
        item.put("vCurr", scale(op.getVCurr(), 2));
        item.put("vMonetary", scale(op.getVMonetary(), 2));
        item.put("vBond", scale(op.getVBond(), 2));
        item.put("vHighVol", scale(op.getVHighVol(), 2));
        item.put("uHigh", scale(op.getUHigh(), 2));
        item.put("uMonetaryDca", scale(op.getUMonetaryDca(), 2));
        item.put("uBondDca", scale(op.getUBondDca(), 2));
        item.put("deltaMTheory", scale(op.getDeltaMTheory(), 2));
        item.put("deltaBTheory", scale(op.getDeltaBTheory(), 2));
        item.put("deltaMActual", scale(op.getDeltaMActual(), 2));
        item.put("deltaBActual", scale(op.getDeltaBActual(), 2));
        item.put("roundingStrategy", op.getRoundingStrategy());
        item.put("budgetLimitUsed", scale(op.getBudgetLimitUsed(), 2));
        item.put("totalInvestment", scale(op.getTotalInvestment(), 2));
        item.put("triggered", op.getTriggeredBoundary() != null && !op.getTriggeredBoundary().isBlank());
        item.put("notes", parseNotes(op.getWarnings()));
        item.put("source", op.getSource());
        return item;
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private Map<String, BigDecimal> sixMap(SnapshotLatestResponse latest) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String cat : SnapshotQueryService.CANONICAL_SIX_CATEGORIES) {
            map.put(cat, BigDecimal.ZERO);
        }
        for (SnapshotCategorySummary s : latest.getCategories()) {
            if (map.containsKey(s.getCategoryName())) {
                map.put(s.getCategoryName(), nz(s.getCategoryTotal()));
            }
        }
        return map;
    }

    private BigDecimal highVolOf(Map<String, BigDecimal> categories) {
        BigDecimal total = BigDecimal.ZERO;
        for (String cat : CorrectionSolveService.HIGH_VOL_CATEGORIES) {
            total = total.add(nz(categories.get(cat)));
        }
        return total;
    }

    private BigDecimal det(Map<String, BigDecimal> targets) {
        BigDecimal pM = frac(targets.get("货币类"), new BigDecimal("10"));
        BigDecimal pB = frac(targets.get("固收类"), new BigDecimal("15"));
        BigDecimal det = BigDecimal.ONE.subtract(pM).subtract(pB);
        return det.signum() <= 0 ? new BigDecimal("0.75") : det;
    }

    private Map<String, BigDecimal> targetRatiosOf(Long userId) {
        return userConfigService.loadSixCategoryTargetRatios(userId);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("2a 校正 target_ratios 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private String toJsonArray(List<String> notes) {
        if (notes == null || notes.isEmpty()) return null;
        return toJson(notes);
    }

    private List<String> parseNotes(String warnings) {
        if (warnings == null || warnings.isBlank()) return Collections.emptyList();
        try {
            return json.readValue(warnings, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(warnings);
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.trim(), DATE_FMT);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal frac(BigDecimal pct, BigDecimal dflt) {
        BigDecimal p = pct != null ? pct : dflt;
        return p.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v, int dp) {
        return v == null ? null : v.setScale(dp, RoundingMode.HALF_UP);
    }
}
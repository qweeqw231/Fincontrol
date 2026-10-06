package com.fincontrol.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.entity.NavHistory;
import com.fincontrol.entity.NavMilestone;
import com.fincontrol.entity.OperationLog;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.NavHistoryMapper;
import com.fincontrol.mapper.NavMilestoneMapper;
import com.fincontrol.mapper.OperationLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Phase 3：净值历史 + 比例时间线查询服务。
 */
@Service
public class NavQueryService {

    private static final Logger log = LoggerFactory.getLogger(NavQueryService.class);

    /** 六大类展示顺序（不含余额类，堆叠面积图用） */
    private static final List<String> SIX_CATEGORIES = List.of(
            "货币类", "固收类", "商品类", "A股权益类", "海外权益类", "港股大中华类"
    );

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final NavHistoryMapper navHistoryMapper;
    private final NavMilestoneMapper navMilestoneMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;
    private final OperationLogMapper operationLogMapper;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public NavQueryService(NavHistoryMapper navHistoryMapper,
                           NavMilestoneMapper navMilestoneMapper,
                           AssetSnapshotMapper assetSnapshotMapper,
                           OperationLogMapper operationLogMapper) {
        this.navHistoryMapper = navHistoryMapper;
        this.navMilestoneMapper = navMilestoneMapper;
        this.assetSnapshotMapper = assetSnapshotMapper;
        this.operationLogMapper = operationLogMapper;
    }

    // ========================================================================
    // GET /api/nav/history — 净值曲线 + 里程碑
    // ========================================================================

    public Map<String, Object> getNavHistory(Long userId) {
        log.info("Phase3 nav/history: userId={}", userId);

        List<NavHistory> navRows = navHistoryMapper.selectAllByUser(userId);
        List<NavMilestone> milestones = navMilestoneMapper.selectAllByUser(userId);

        List<Map<String, Object>> points = new ArrayList<>(navRows.size());
        for (NavHistory row : navRows) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", row.getNavDate().toString());
            p.put("nav", scale(row.getNav(), 6));
            p.put("navPct", scale(row.getNavPct(), 4));
            p.put("dailyReturnPct", scale(row.getDailyReturnPct(), 6));
            p.put("actualProfit", scale(row.getActualProfit(), 2));
            p.put("cumulativeProfit", scale(row.getCumulativeProfit(), 2));
            p.put("totalAsset", scale(row.getTotalAsset(), 2));
            points.add(p);
        }

        List<Map<String, Object>> milestonePoints = new ArrayList<>(milestones.size());
        for (NavMilestone m : milestones) {
            Map<String, Object> mp = new LinkedHashMap<>();
            mp.put("date", m.getMilestoneDate().toString());
            mp.put("category", m.getCategory());
            mp.put("nav", scale(m.getNav(), 6));
            mp.put("navPct", scale(m.getNavPct(), 4));
            mp.put("cumulativeProfit", scale(m.getCumulativeProfit(), 2));
            mp.put("description", m.getDescription());
            milestonePoints.add(mp);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", points);
        result.put("milestones", milestonePoints);
        return result;
    }

    // ========================================================================
    // GET /api/ratio/history — 六大类占比时间线
    // ========================================================================

    /**
     * 比例时间线。
     *
     * <p>2026-10-07 修复（比例塌 0 事故）：占比不再直接读 {@code asset_snapshot.actual_ratio}
     * 冗余列（confirm 流程在 AI 百分比缺失时写 0，导致 9/29、10/6 两点全塌），
     * 改为按权威金额实时重算（对齐 1b.3 R4 口径）：
     * <ul>
     *   <li>六大类 ratio = category / 六大类合计 × 100（合计恰为 100）</li>
     *   <li>余额类 ratio = 余额 / 总资产（六大类 + 余额）× 100（占总资产口径）</li>
     * </ul>
     * 已用 9/24 等导入日期交叉验证：重算值与存量列一致，历史曲线不变。
     */
    public Map<String, Object> getRatioHistory(Long userId) {
        log.info("Phase3 ratio/history: userId={}", userId);

        LambdaQueryWrapper<AssetSnapshot> qw = new LambdaQueryWrapper<>();
        qw.eq(AssetSnapshot::getUserId, userId)
          .eq(AssetSnapshot::getIsLatest, true)
          .orderByAsc(AssetSnapshot::getSnapshotDate)
          .orderByAsc(AssetSnapshot::getCategory);
        List<AssetSnapshot> rows = assetSnapshotMapper.selectList(qw);

        // Group by date（先归集金额，再按日期重算占比）
        Map<String, Map<String, BigDecimal>> amountByDate = new LinkedHashMap<>();
        for (AssetSnapshot snap : rows) {
            String dateStr = snap.getSnapshotDate().toString();
            amountByDate.computeIfAbsent(dateStr, k -> new LinkedHashMap<>())
                    .merge(snap.getCategory(), nz(snap.getTotalAmount()), BigDecimal::add);
        }

        List<Map<String, Object>> points = new ArrayList<>(amountByDate.size());
        for (Map.Entry<String, Map<String, BigDecimal>> entry : amountByDate.entrySet()) {
            Map<String, BigDecimal> amounts = entry.getValue();
            BigDecimal sixTotal = BigDecimal.ZERO;
            BigDecimal totalAll = BigDecimal.ZERO;
            for (BigDecimal amount : amounts.values()) {
                totalAll = totalAll.add(amount);
            }
            for (Map.Entry<String, BigDecimal> a : amounts.entrySet()) {
                if (!"余额类".equals(a.getKey())) {
                    sixTotal = sixTotal.add(a.getValue());
                }
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", entry.getKey());
            for (Map.Entry<String, BigDecimal> a : amounts.entrySet()) {
                String cat = a.getKey();
                BigDecimal denominator = "余额类".equals(cat) ? totalAll : sixTotal;
                BigDecimal ratio = denominator.signum() > 0
                        ? a.getValue().multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP)
                        : null;
                point.put(cat, ratio);
                point.put(cat + "_amount", a.getValue().setScale(2, RoundingMode.HALF_UP));
            }
            points.add(point);
        }
        // Sort by date
        points.sort(Comparator.comparing(p -> (String) p.get("date")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", points);
        result.put("categories", SIX_CATEGORIES);
        return result;
    }

    // ========================================================================
    // GET /api/nav/operations — 校正与操作记录（operation_log）
    // ========================================================================

    public Map<String, Object> getOperations(Long userId) {
        log.info("Phase3 nav/operations: userId={}", userId);

        List<OperationLog> rows = operationLogMapper.selectAllByUser(userId);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (OperationLog op : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", op.getOperationDate() == null ? null
                    : op.getOperationDate().format(DATE_FMT));
            item.put("operationType", op.getOperationType());
            item.put("snapshotDate", op.getSnapshotDate() == null ? null
                    : op.getSnapshotDate().toString());
            item.put("vCurr", scale(op.getVCurr(), 2));
            item.put("vMonetary", scale(op.getVMonetary(), 2));
            item.put("vBond", scale(op.getVBond(), 2));
            item.put("vHighVol", scale(op.getVHighVol(), 2));
            item.put("uHigh", scale(op.getUHigh(), 2));
            item.put("deltaMTheory", scale(op.getDeltaMTheory(), 2));
            item.put("deltaBTheory", scale(op.getDeltaBTheory(), 2));
            item.put("deltaMActual", scale(op.getDeltaMActual(), 2));
            item.put("deltaBActual", scale(op.getDeltaBActual(), 2));
            item.put("roundingStrategy", op.getRoundingStrategy());
            item.put("budgetLimitUsed", scale(op.getBudgetLimitUsed(), 2));
            item.put("totalInvestment", scale(op.getTotalInvestment(), 2));
            item.put("triggered", op.getTriggeredBoundary() != null
                    && !op.getTriggeredBoundary().isBlank());
            item.put("notes", parseNotes(op.getWarnings()));
            item.put("source", op.getSource());
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        return result;
    }

    /** warnings 列为 JSON 数组字符串，解析失败时降级为单元素列表 */
    private List<String> parseNotes(String warnings) {
        if (warnings == null || warnings.isBlank()) return Collections.emptyList();
        try {
            return JSON.readValue(warnings, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("operation_log.warnings 解析失败，降级为纯文本：{}", e.getMessage());
            return List.of(warnings);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private BigDecimal scale(BigDecimal v, int dp) {
        if (v == null) return null;
        return v.setScale(dp, RoundingMode.HALF_UP);
    }
}

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

    public Map<String, Object> getRatioHistory(Long userId) {
        log.info("Phase3 ratio/history: userId={}", userId);

        LambdaQueryWrapper<AssetSnapshot> qw = new LambdaQueryWrapper<>();
        qw.eq(AssetSnapshot::getUserId, userId)
          .eq(AssetSnapshot::getIsLatest, true)
          .orderByAsc(AssetSnapshot::getSnapshotDate)
          .orderByAsc(AssetSnapshot::getCategory);
        List<AssetSnapshot> rows = assetSnapshotMapper.selectList(qw);

        // Group by date
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (AssetSnapshot snap : rows) {
            String dateStr = snap.getSnapshotDate().toString();
            Map<String, Object> point = byDate.computeIfAbsent(dateStr, k -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("date", k);
                return p;
            });
            String cat = snap.getCategory();
            point.put(cat, scale(snap.getActualRatio(), 2));
            point.put(cat + "_amount", scale(snap.getTotalAmount(), 2));
        }

        List<Map<String, Object>> points = new ArrayList<>(byDate.values());
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

    // ========================================================================
    // Helper
    // ========================================================================

    private BigDecimal scale(BigDecimal v, int dp) {
        if (v == null) return null;
        return v.setScale(dp, RoundingMode.HALF_UP);
    }
}

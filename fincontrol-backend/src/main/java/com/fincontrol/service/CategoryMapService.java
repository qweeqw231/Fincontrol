package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.CategoryEnum;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.category.CategoryMapMatchItem;
import com.fincontrol.dto.category.CategoryMapMatchResponse;
import com.fincontrol.dto.category.CategoryMapUpdateResponse;
import com.fincontrol.entity.FundCategoryMap;
import com.fincontrol.mapper.FundCategoryMapMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 1a.5 大类映射服务（[api-contract.md §7.1 / §7.2](#)，[P0-1.3] UPDATE 规则）。
 *
 * <p>两条入口：
 * <ul>
 *   <li>{@link #match(Long, String)} — 批量查当前 user 的映射，最多 50 个 fund</li>
 *   <li>{@link #update(Long, String, String)} — 单条按 (user_id, fund_name) 唯一索引 UPDATE/INSERT</li>
 * </ul>
 *
 * <p>写入路径与 1a.3 confirm 共用 {@code upsertByFundName}：XML 端 {@code ON CONFLICT DO UPDATE SET
 * source = EXCLUDED.source}，传什么 source 就写什么 source；故 1a.5 update 在 Service 侧控制 source：
 * <ul>
 *   <li>已存在行 → {@code user_correct}（用户纠正）</li>
 *   <li>新行 → {@code user_manual}（用户手动新增）</li>
 * </ul>
 *
 * <p>1a.3 confirm 维持原有直接调用 {@code upsertByFundName} 不变（始终传 {@code user_correct}）；
 * 与 1a.5 update 的"已存在行"路径语义完全等价，无需协调冲突。
 */
@Service
public class CategoryMapService {

    private static final Logger log = LoggerFactory.getLogger(CategoryMapService.class);

    /** 1a.5 match 接口单次最多查询基金数（[api-contract.md §7.1](#)） */
    public static final int MATCH_FUNDS_LIMIT = 50;

    private final FundCategoryMapMapper fundCategoryMapMapper;
    private final CategoryMasterService categoryMasterService;

    /** 保留纯单测构造器；迁移前按 CategoryEnum 校验。 */
    public CategoryMapService(FundCategoryMapMapper fundCategoryMapMapper) {
        this(fundCategoryMapMapper, null);
    }

    @Autowired
    public CategoryMapService(FundCategoryMapMapper fundCategoryMapMapper,
                              CategoryMasterService categoryMasterService) {
        this.fundCategoryMapMapper = fundCategoryMapMapper;
        this.categoryMasterService = categoryMasterService;
    }

    // ========================================================================
    // 1a.16 match
    // ========================================================================

    /**
     * 批量查询当前 user 的基金-大类映射（[api-contract.md §7.1](#)）。
     *
     * <p>{@code fundsCsv} 为逗号分隔字符串；自动 trim + 去空 + 去重 + 长度上限校验。
     *
     * @param userId   用户 ID（来自 X-User-Id，默认 1）
     * @param fundsCsv 逗号分隔的基金名称列表（最多 50 个唯一基金）
     * @return matchedFunds + unmatchedFunds
     * @throws BusinessException {@code FUNDS_COUNT_EXCEEDS_LIMIT(1002)} 当唯一基金数 > 50
     */
    public CategoryMapMatchResponse match(Long userId, String fundsCsv) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }

        // Step 1: CSV 解析 → LinkedHashSet 保序去重
        List<String> uniqueFunds = parseFundsCsv(fundsCsv);

        // Step 2: 上限校验
        if (uniqueFunds.size() > MATCH_FUNDS_LIMIT) {
            throw new BusinessException(ErrorCode.FUNDS_COUNT_EXCEEDS_LIMIT,
                    "funds 数量 " + uniqueFunds.size() + " 超过上限 " + MATCH_FUNDS_LIMIT);
        }

        // Step 3: 边界 — 空查询直接返空响应
        if (uniqueFunds.isEmpty()) {
            log.info("1a.5 match: userId={} funds 空，返回 matched=[] unmatched=[]", userId);
            return CategoryMapMatchResponse.builder()
                    .matchedFunds(Collections.emptyList())
                    .unmatchedFunds(Collections.emptyList())
                    .build();
        }

        // 1b.4 PR9 Bug 1：fund_name 字符串 normalize
        // AI 解析返回的 fundName 可能含：全角空格 (\u3000)、多余连续空白、大小写差异。
        // 用户手动 user_correct 保存时也可能含这些差异（不同 source 抹平后）。
        // 服务端 SQL 也用 TRIM + REPLACE 全角空格折叠，保证双向匹配。
        // 返回给前端的 fundName 用 AI 原始名（保留 dropdown 显示），category 用 user_correct。
        List<String> normalizedQuery = new ArrayList<>(uniqueFunds.size());
        for (String name : uniqueFunds) {
            String n = normalizeFundName(name);
            if (n != null && !n.isEmpty()) {
                normalizedQuery.add(n);
            }
        }

        // Step 4: 批量查（一次往返完成 N 个 fund 查表，SQL 端做 TRIM + 全角空格折叠）
        List<FundCategoryMap> rows = fundCategoryMapMapper.selectByUserAndNormalizedNames(userId, normalizedQuery);
        if (rows == null) {
            rows = Collections.emptyList();
        }
        // 以 normalize 后的 fund_name 为 key 建索引（这样原始 + 折叠后的同名能命中）
        Map<String, FundCategoryMap> byName = new HashMap<>(rows.size());
        for (FundCategoryMap row : rows) {
            String normalizedKey = normalizeFundName(row.getFundName());
            if (normalizedKey != null) {
                byName.put(normalizedKey, row);
            }
        }

        // Step 5: 按输入顺序分桶（matched + unmatched）
        List<CategoryMapMatchItem> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (String name : uniqueFunds) {
            String normalized = normalizeFundName(name);
            FundCategoryMap row = normalized == null ? null : byName.get(normalized);
            if (row == null) {
                unmatched.add(name);
            } else {
                matched.add(CategoryMapMatchItem.builder()
                        .fundName(row.getFundName())
                        .category(row.getCategory())
                        .source(row.getSource())
                        .confirmedAt(row.getConfirmedAt())
                        // 决策 33 D7（R5）：填充消失-重现状态字段
                        .lastSeenSnapshotDate(row.getLastSeenSnapshotDate())
                        .firstMissingSnapshotDate(row.getFirstMissingSnapshotDate())
                        .build());
            }
        }

        log.info("1b.4 PR9 match: userId={} input={} matched={} unmatched={}",
                userId, uniqueFunds.size(), matched.size(), unmatched.size());
        return CategoryMapMatchResponse.builder()
                .matchedFunds(matched)
                .unmatchedFunds(unmatched)
                .build();
    }

    /**
     * 1b.4 PR9 Bug 1：fund_name 字符串 normalize。
     * <p>去除所有空白字符（空格、tab、LF、CR、全角空格）。
     * <p>SQL 端应用同一折叠算法（详见 mapper.xml 中 selectByUserAndNormalizedNames）。
     * <p>金融场景下 "易方达 蓝筹" / "易方达蓝筹" / "易方达　蓝筹" 视为同一只基金（仅排版差异）。
     *
     * @param name 原始 fund_name（可能含 \u3000、tab、连续空白）
     * @return normalize 后字符串；null / 空输入返 null
     */
    static String normalizeFundName(String name) {
        if (name == null) return null;
        String s = name.trim();
        if (s.isEmpty()) return null;
        // 全角空格 (\u3000) → 半角空格；tab/CR/LF → 半角空格
        s = s.replace('\u3000', ' ').replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        // 折叠连续空格为单个
        s = s.replaceAll("\\s+", " ");
        // 关键：最终去除所有空白（SQL 端 REPLACE all whitespace to ''）
        s = s.replace(" ", "");
        return s;
    }

    /** 解析 CSV：trim → 去空 → 去重（保留首次出现顺序）。 */
    static List<String> parseFundsCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        String[] tokens = csv.split(",");
        LinkedHashSet<String> set = new LinkedHashSet<>(tokens.length);
        for (String token : tokens) {
            String trimmed = token == null ? "" : token.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return new ArrayList<>(set);
    }

    // ========================================================================
    // 1a.17 update
    // ========================================================================

    /**
     * 单条更新映射（[api-contract.md §7.2](#)，[P0-1.3] UPDATE 规则）。
     *
     * <p>分支：
     * <ul>
     *   <li>已存在行 → {@code UPDATE}，{@code source='user_correct'}，{@code updated=true}</li>
     *   <li>新行 → {@code INSERT}，{@code source='user_manual'}，{@code updated=false}</li>
     * </ul>
     *
     * @throws BusinessException {@code INVALID_CATEGORY_NAME(1004)} 入参非法
     */
    public CategoryMapUpdateResponse update(Long userId, String fundName, String category) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        validateFundName(fundName);
        validateCategory(category);

        // Step 1: 查询现有映射，决定 UPDATE vs INSERT
        FundCategoryMap existing = fundCategoryMapMapper.selectByUserAndFundName(userId, fundName);
        boolean isUpdate = existing != null;

        // Step 2: 构造 entity + 调用 upsert
        FundCategoryMap map = new FundCategoryMap();
        map.setUserId(userId);
        map.setFundName(fundName);
        map.setCategory(category);
        if (isUpdate) {
            // [P0-1.3] 已有映射：source 固定为 user_correct
            map.setSource("user_correct");
        } else {
            // 新映射：source 固定为 user_manual
            map.setSource("user_manual");
        }
        // confirmedAt 由 XML 端 CURRENT_TIMESTAMP 写入，这里不需要 set
        int affected = fundCategoryMapMapper.upsertByFundName(map);
        log.info("1a.5 update: userId={} fund={} category={} isUpdate={} affected={}",
                userId, fundName, category, isUpdate, affected);

        // Step 3: 回读拿 mappingId / confirmedAt（XML 的 CURRENT_TIMESTAMP）
        FundCategoryMap after = fundCategoryMapMapper.selectByUserAndFundName(userId, fundName);
        if (after == null) {
            // 极端情况：upsert 后回读失败 → 抛 5002
            throw new BusinessException(ErrorCode.DATABASE_ERROR,
                    "upsertByFundName 后回读失败: userId=" + userId + " fund=" + fundName);
        }

        return CategoryMapUpdateResponse.builder()
                .mappingId(after.getId())
                .fundName(after.getFundName())
                .category(after.getCategory())
                .source(after.getSource())
                .confirmedAt(after.getConfirmedAt())
                .updated(isUpdate)
                .build();
    }

    private static void validateFundName(String fundName) {
        if (fundName == null || fundName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY_NAME, "fundName 必填且非空");
        }
    }

    private void validateCategory(String category) {
        boolean valid = categoryMasterService != null
                ? categoryMasterService.isActiveCanonical(category)
                : CategoryEnum.isValid(category);
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY_NAME,
                    "category '" + category + "' 不在 7 canonical 枚举值内（货币类/固收类/商品类/A股权益类/海外权益类/港股大中华类/余额类）");
        }
    }

    // ========================================================================
    // 1a.8.8 决策 8：DELETE / reset / stale（多用户债修复 + stale 判定）
    // ========================================================================

    /**
     * 1a.8.8 DELETE：删除单条映射。不跨 user。未命中返 0。
     */
    public int delete(Long userId, String fundName) {
        if (userId == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        validateFundName(fundName);
        int affected = fundCategoryMapMapper.deleteByUserAndFundName(userId, fundName);
        log.info("1a.8.8 delete: userId={} fund={} affected={}", userId, fundName, affected);
        return affected;
    }

    /**
     * 1a.8.8 reset：重置映射为 ai_guess（不删行，不改 category，只改 source 让 re-confirm 走正常路径）。
     * <p>用于用户主动让前端弹出确认窗（即使上次已 user_correct）。
     */
    public int reset(Long userId, String fundName) {
        if (userId == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        validateFundName(fundName);
        int affected = fundCategoryMapMapper.updateLastSeen(userId, fundName, "ai_guess");
        log.info("1a.8.8 reset: userId={} fund={} affected={}", userId, fundName, affected);
        return affected;
    }

    /**
     * 1a.8.8 stale：列 last_seen_at 早于 now-days 的 user_correct 映射（供前端 review）。
     */
    public java.util.List<FundCategoryMap> listStale(Long userId, int days) {
        if (userId == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        if (days < 0) throw new BusinessException(ErrorCode.INVALID_CATEGORY_NAME, "days 必须 ≥ 0");
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(days);
        java.util.List<FundCategoryMap> rows = fundCategoryMapMapper.selectStaleByUser(userId, cutoff);
        log.info("1a.8.8 stale: userId={} days={} cutoff={} returned={}",
                userId, days, cutoff, rows == null ? 0 : rows.size());
        return rows == null ? java.util.Collections.emptyList() : rows;
    }
}

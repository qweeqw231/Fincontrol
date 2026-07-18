package com.fincontrol.service;

import com.fincontrol.common.CategoryEnum;
import com.fincontrol.entity.FundCategoryMap;
import com.fincontrol.mapper.FundCategoryMapMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 1a.8.8 决策 8：基金-类别归一化解析器。
 *
 * <p>读路径（{@link #resolve}）从 fund_category_map 查 4 状态映射：
 * <ol>
 *   <li><b>user_correct 命中</b>：返 canonical + {@code isUserConfirmed=true}（最高优先级）</li>
 *   <li><b>ai_guess 命中</b>：返 canonical + {@code isUserConfirmed=false}（兜底）</li>
 *   <li><b>未知名 rawCategory</b>：{@link CategoryEnum#fromAlias(String)} 归一化 → canonical；fallback 返 raw 本身</li>
 *   <li><b>都未命中</b>：返 rawCategory 兜底 + {@code isUserConfirmed=false}</li>
 * </ol>
 *
 * <p>解析过程不写库（保证幂等 + 易测试）。写路径由 {@link SnapShotConfirmService#writeFundCategoryMap}
 * 在 confirm 阶段完成（首次 ai_guess / 已存在 user_correct）。
 *
 * <p>多用户隔离：所有查表都带 {@code userId}，不存在跨 user 命中。
 */
@Service
public class FundCategoryResolver {

    private static final Logger log = LoggerFactory.getLogger(FundCategoryResolver.class);

    private final FundCategoryMapMapper fundCategoryMapMapper;

    public FundCategoryResolver(FundCategoryMapMapper fundCategoryMapMapper) {
        this.fundCategoryMapMapper = fundCategoryMapMapper;
    }

    /** Resolver 输出值对象。 */
    public record ResolvedCategory(String canonicalName, boolean isUserConfirmed) {}

    /**
     * 解析基金类别（不写库）。
     *
     * @param fundName    基金名（不可空）
     * @param rawCategory AI/前端的原始类别名（可空；空时视为"其他"）
     * @param userId      用户 ID（多用户隔离）
     * @return canonical + isUserConfirmed；无任何信息时返 {@code ResolvedCategory(rawCategory, false)}
     */
    public ResolvedCategory resolve(String fundName, String rawCategory, Long userId) {
        if (fundName == null || fundName.isBlank()) {
            return new ResolvedCategory(safeRaw(rawCategory), false);
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 必填");
        }

        // 1) user_correct 命中（最高优先级）
        FundCategoryMap userCorrect = fundCategoryMapMapper.selectByUserCorrect(userId, fundName);
        if (userCorrect != null) {
            log.debug("resolver hit user_correct: userId={} fund={} → {} (last_seen_at={})",
                    userId, fundName, userCorrect.getCategory(), userCorrect.getLastSeenAt());
            return new ResolvedCategory(userCorrect.getCategory(), true);
        }

        // 2) ai_guess / user_manual 命中（按 fund_name 查任意 source）
        FundCategoryMap any = fundCategoryMapMapper.selectByUserAndFundName(userId, fundName);
        if (any != null) {
            log.debug("resolver hit any-source: userId={} fund={} source={} → {}",
                    userId, fundName, any.getSource(), any.getCategory());
            return new ResolvedCategory(any.getCategory(), false);
        }

        // 3) 未知名归一化（fromAlias）
        String canonical = CategoryEnum.fromAlias(rawCategory);
        if (canonical != null) {
            log.debug("resolver hit fromAlias: fund={} raw={} → {}", fundName, rawCategory, canonical);
            return new ResolvedCategory(canonical, false);
        }

        // 4) fallback：保留 raw 供前端高亮 + 抛 1004 错位（不抛错，解析路径保持幂等）
        log.warn("resolver fallback: fund={} raw={} 未命中任何映射，保留 raw 供前端高亮",
                fundName, rawCategory);
        return new ResolvedCategory(safeRaw(rawCategory), false);
    }

    /**
     * 列 stale user_correct 映射（last_seen_at 早于 now-days）。
     * <p>默认 90 天（决策 8）；前端可覆盖。
     */
    public java.util.List<FundCategoryMap> listStale(Long userId, int days) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 必填");
        }
        if (days < 0) {
            throw new IllegalArgumentException("days 必须 ≥ 0");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return fundCategoryMapMapper.selectStaleByUser(userId, cutoff);
    }

    private static String safeRaw(String raw) {
        return (raw == null || raw.isBlank()) ? "其他" : raw.trim();
    }
}
package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.FundCategoryMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * fund_category_map 表 DAO（[db-schema.sql §3](#)）。
 *
 * <p>自定义 SQL 在 {@code resources/mapper/FundCategoryMapMapper.xml} 双方言实现。
 */
@Mapper
public interface FundCategoryMapMapper extends BaseMapper<FundCategoryMap> {

    /**
     * 1a.3 confirm 写入：UPSERT（user_id, fund_name 唯一索引）。
     * <p>实现见 XML — MySQL ON DUPLICATE KEY UPDATE，H2 MERGE INTO。
     */
    int upsertByFundName(FundCategoryMap map);

    /**
     * 1a.5 暴露给前端：按 fund_name 查单条。
     */
    FundCategoryMap selectByUserAndFundName(
            @Param("userId") Long userId,
            @Param("fundName") String fundName);

    /**
     * 1a.5 暴露给前端：按 fund_name 集合批量查询（避免 N+1）。
     * <p>实现见 XML — MySQL 8.0.20+ 与 H2 均支持 {@code IN (...)}。
     */
    List<FundCategoryMap> selectByUserAndFundNames(
            @Param("userId") Long userId,
            @Param("fundNames") Collection<String> fundNames);

    /**
     * 1a.3 confirm 维度 D 镜像校验：查同 user 下 fund_name 集合。
     */
    List<String> selectFundNamesByUser(@Param("userId") Long userId);

    /**
     * 1a.3 confirm 维度 E 检测：同 user_id+date 下 fund_name 集合。
     */
    Set<String> selectFundNamesByUserAndSnapshotDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") LocalDate snapshotDate);

    // ================================================================
    // 1a.8.8 决策 8：类别归一化 + 双向 cache + stale 判定
    // ================================================================

    /**
     * 1a.8.8 Resolver 查 user_correct 映射（优先级最高）。
     */
    FundCategoryMap selectByUserCorrect(
            @Param("userId") Long userId,
            @Param("fundName") String fundName);

    /**
     * 1a.8.8 列 stale user_correct 映射（last_seen_at 早于 cutoffDate）。
     * <p>cutoffDate 在 Java 端计算为 {@code LocalDateTime.now().minusDays(days)}，
     * SQL 端不做 INTERVAL 计算（双方言兼容）。
     */
    List<FundCategoryMap> selectStaleByUser(
            @Param("userId") Long userId,
            @Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * 1a.8.8 re-confirm 二态写：仅更新 last_seen_at（保留 source/category，不重新打 user_correct）。
     * <p>用于已有 ai_guess 行被快照再次覆盖：不需要重写 source，只需要证明出现过。
     */
    int updateLastSeen(
            @Param("userId") Long userId,
            @Param("fundName") String fundName,
            @Param("source") String source);

    /**
     * 1a.8.8 DELETE 接口：单用户删除映射（不跨 user）。
     */
    int deleteByUserAndFundName(
            @Param("userId") Long userId,
            @Param("fundName") String fundName);
}

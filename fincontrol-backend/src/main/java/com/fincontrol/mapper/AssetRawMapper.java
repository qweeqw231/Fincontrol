package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.AssetRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Set;

/**
 * asset_raw 表 DAO（[db-schema.sql §1](#)）。
 *
 * <p>继承 {@link BaseMapper} 获得 CRUD，附加 1a.3 confirm 写库 + 1a.8 撤销的 4 个自定义方法。
 */
@Mapper
public interface AssetRawMapper extends BaseMapper<AssetRaw> {

    /**
     * 维度 D 镜像校验：同 user_id+date+category 下 amount 之和。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM asset_raw " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} AND category = #{category}")
    java.math.BigDecimal sumAmountByUserAndDateAndCategory(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate,
            @Param("category") String category);

    /**
     * 维度 D 镜像校验：同 user_id+date 下 fund_name 集合（与 fund_category_map 对比）。
     */
    @Select("SELECT DISTINCT fund_name FROM asset_raw " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate}")
    Set<String> selectFundNamesByUserAndDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate);

    /**
     * 1a.3 confirm 阶段：插入 1 条 asset_raw 记录（BaseMapper.insert 已覆盖，但保留扩展点）。
     */
    // 由 BaseMapper 提供，1a.3 阶段直接用 assetRawMapper.insert(row)

    /**
     * 1a.8 撤销：把整批 is_latest 翻成 false。
     */
    @Update("UPDATE asset_raw SET is_latest = false " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate}")
    int updateIsLatestBySnapshotDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate);

    /**
     * 1a.3 单测 / 1a.4 整合测试：列出某个 user 的全部 fund_name（去重）。
     */
    @Select("SELECT DISTINCT fund_name FROM asset_raw WHERE user_id = #{userId}")
    List<String> selectAllFundNamesByUser(@Param("userId") Long userId);

    /**
     * 1a.4 快照查询：取 user/date/category 下 is_latest=true 的基金行，按创建时间排序。
     */
    @Select("SELECT * FROM asset_raw " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} AND category = #{category} " +
            "AND is_latest = true " +
            "ORDER BY created_at ASC, id ASC")
    List<AssetRaw> selectByUserAndDateAndCategory(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate,
            @Param("category") String category);

    /**
     * 1a.4 快照查询：统计 user/date/category 下 is_latest=true 的基金行数。
     */
    @Select("SELECT COUNT(*) FROM asset_raw " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} AND category = #{category} " +
            "AND is_latest = true")
    int countFundsByUserAndDateAndCategory(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate,
            @Param("category") String category);

    /**
     * 1a.4 余额查询：列出 user 当前最新一版（is_latest=true）的"余额类"行。
     */
    @Select("SELECT * FROM asset_raw " +
            "WHERE user_id = #{userId} " +
            "AND category = '余额类' " +
            "AND is_latest = true " +
            "ORDER BY snapshot_date DESC, id DESC")
    List<AssetRaw> selectBalanceByUser(@Param("userId") Long userId);

    /**
     * 1b.2 累计收益率（决策 4 v2 / 口径 A / 2026-07-22）：Σcumulative_profit 与 Σamount，按 user 全部 is_latest=true 行计算。
     * <p>口径 A = 全口径含余额类（user 拍板），与决策 7 / 8 / 13 一致：分母不去余额类，分子也含余额类（如余额宝 cumulative）。
     */
    @Select("SELECT COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit, " +
            "COALESCE(SUM(amount), 0) AS total_amount " +
            "FROM asset_raw " +
            "WHERE user_id = #{userId} AND is_latest = 1")
    java.util.Map<String, Object> sumCumProfitAndAmountByUser(@Param("userId") Long userId);
}



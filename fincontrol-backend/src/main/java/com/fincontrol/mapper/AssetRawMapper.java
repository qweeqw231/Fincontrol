package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.AssetRaw;
import org.apache.ibatis.annotations.Insert;
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
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} " +
            "AND category = #{category} AND is_latest = true")
    java.math.BigDecimal sumAmountByUserAndDateAndCategory(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate,
            @Param("category") String category);

    /**
     * 维度 D 镜像校验：同 user_id+date 下 fund_name 集合（与 fund_category_map 对比）。
     */
    @Select("SELECT DISTINCT fund_name FROM asset_raw " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} " +
            "AND is_latest = true")
    Set<String> selectFundNamesByUserAndDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate);

    /**
     * 1b.4-pr7 (DATA-016) Fix 4：幂等 upsert（user_id, snapshot_date, fund_name 唯一键）。
     * <p>首次入库 → INSERT；二次确认同 (user_id, snapshot_date, fund_name) → UPDATE 覆盖 amount / profit /
     * holding_profit / cumulative_profit / total_asset_source / is_latest=true / confirmed_at。
     * <p>语义与 {@code AssetSnapshotMapper.upsertByCategory} / {@code FundCategoryMapMapper.upsertByFundName} 对称：
     * 之前 {@code writeAssetRaw} 纯用 {@link com.baomidou.mybatisplus.core.mapper.BaseMapper#insert} 会在二次
     * confirm 同 snapshot_date 时撞 PK 报 500，本方法改用 ON DUPLICATE KEY UPDATE 实现幂等 overwrite。
     * <p>MySQL 8.0+ / H2 1.4.197+ 均支持 ON DUPLICATE KEY UPDATE 语法。
     * <p>注意：is_latest 必须在写入前由 service 层调 {@link #updateIsLatestBySnapshotDate} 先翻旧行 false，
     * 本 UPSERT 仅负责当前行写入（与 writeAssetSnapshot 行为一致）。
     */
    @Insert("INSERT INTO asset_raw " +
            "(user_id, snapshot_date, fund_name, fund_code, category, amount, " +
            " profit, holding_profit, cumulative_profit, source, total_asset_source, " +
            " is_latest, created_at, confirmed_at) " +
            "VALUES " +
            "(#{userId}, #{snapshotDate}, #{fundName}, #{fundCode}, #{category}, #{amount}, " +
            " #{profit}, #{holdingProfit}, #{cumulativeProfit}, #{source}, " +
            " COALESCE(#{totalAssetSource}, 'top'), " +
            " true, CURRENT_TIMESTAMP, #{confirmedAt}) " +
            "ON DUPLICATE KEY UPDATE " +
            " fund_code = VALUES(fund_code), " +
            " category = VALUES(category), " +
            " amount = VALUES(amount), " +
            " profit = VALUES(profit), " +
            " holding_profit = VALUES(holding_profit), " +
            " cumulative_profit = VALUES(cumulative_profit), " +
            " total_asset_source = VALUES(total_asset_source), " +
            " is_latest = true, " +
            " confirmed_at = VALUES(confirmed_at)")
    int upsertByFundName(AssetRaw row);

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
     * 1b.2 累计 + 持有 收益（决策 4 v2 / 决策 25 v2 / 口径 A / 2026-07-22）：
     * 返 5 个字段（累计 profit / 持有 profit / 总额 / 基金行数 / snapshot_date）。
     * **双保险**：额外加 `snapshot_date = MAX(snapshot_date)` 过滤，避免 is_latest 标记错乱时混入旧数据。
     * <p>累计 vs 持有 区别：
     * <ul>
     *   <li>累计（cumulative）：自该基金建仓以来所有盈亏总和（含已实现）
     *   <li>持有（holding）：当前仍持仓的浮盈/亏（不含已实现）
     * </ul>
     * <p>Phase 3 升级为 Modified Dietz / XIRR 时，本方法改为分支计算，算法标识从 phase1_simple 改为 phase3_dietz / phase3_xirr。
     * <p>未来实现「每一天的累计/持有」需新增方法 `getReturnAtDate(userId, snapshotDate)`（Phase 2）。
     */
    @Select("SELECT COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit, " +
            "COALESCE(SUM(holding_profit), 0) AS total_holding_profit, " +
            "COALESCE(SUM(amount), 0) AS total_amount, " +
            "COUNT(*) AS fund_count, " +
            "MAX(snapshot_date) AS snapshot_date " +
            "FROM asset_raw " +
            "WHERE user_id = #{userId} " +
            "AND is_latest = 1 " +
            "AND snapshot_date = (SELECT MAX(snapshot_date) FROM asset_raw " +
            "                       WHERE user_id = #{userId} AND is_latest = 1)")
    java.util.Map<String, Object> sumReturnFieldsByUser(@Param("userId") Long userId);

    /**
     * 1b.3 P7 修复：定向把指定 (user, fund, dates) 的 category 改为 newCategory。
     * 仅供 1b.3 一次性数据修复脚本使用（长城短债债券A / 鹏华纯债债券D 误归 A股权益类 改回 固收类）。
     * <p>使用：{@code UPDATE asset_raw SET category=#{newCategory} WHERE user_id=#{userId} AND fund_name=#{fundName} AND snapshot_date IN (...)}。
     * <p>is_latest 状态不变；只动 is_latest=1 行（保留历史行不动以备审计）。
     */
    @Update("<script>" +
            "UPDATE asset_raw SET category = #{newCategory} " +
            "WHERE user_id = #{userId} " +
            "AND fund_name = #{fundName} " +
            "AND is_latest = 1 " +
            "AND snapshot_date IN " +
            "<foreach collection='snapshotDates' item='d' open='(' separator=',' close=')'>" +
            "#{d}" +
            "</foreach>" +
            "</script>")
    int updateCategoryByUserAndFundAndDates(
            @Param("userId") Long userId,
            @Param("fundName") String fundName,
            @Param("newCategory") String newCategory,
            @Param("snapshotDates") List<java.time.LocalDate> snapshotDates);
}



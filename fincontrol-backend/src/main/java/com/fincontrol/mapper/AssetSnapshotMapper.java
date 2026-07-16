package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.AssetSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * asset_snapshot 表 DAO（[db-schema.sql §2](#)）。
 *
 * <p>继承 {@link BaseMapper} 获得 CRUD，附加 1a.3 confirm 写库 + 1a.8 撤销的 4 个自定义方法。
 *
 * <p>写入语义：{@code upsertByCategory} 用 {@code ON DUPLICATE KEY UPDATE}（MySQL 5.7+）；
 * H2 测试时 {@code MODE=MySQL} 兼容。
 */
@Mapper
public interface AssetSnapshotMapper extends BaseMapper<AssetSnapshot> {

    /**
     * 1a.3 confirm 写库：UPSERT（user_id, snapshot_date, category, is_latest=true）。
     *
     * <p>语义：若同 (user_id, snapshot_date, category) 已存在 is_latest=true 行则更新（total_amount / actual_ratio / balance_fund / updated_at）；
     * 否则插入新行。
     */
    @Insert("INSERT INTO asset_snapshot " +
            "(user_id, snapshot_date, category, total_amount, target_ratio, actual_ratio, " +
            "balance_fund, sub_detail, is_latest, created_at, updated_at) " +
            "VALUES (#{userId}, #{snapshotDate}, #{category}, #{totalAmount}, #{targetRatio}, " +
            "#{actualRatio}, #{balanceFund}, #{subDetail}, true, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "total_amount = VALUES(total_amount), actual_ratio = VALUES(actual_ratio), " +
            "balance_fund = VALUES(balance_fund), sub_detail = VALUES(sub_detail), " +
            "is_latest = true, updated_at = NOW()")
    @Options(useGeneratedKeys = true)
    int upsertByCategory(AssetSnapshot snap);

    /**
     * 维度 D 镜像校验：count is_latest=true 行（同 user_id+date+category 应只 1 行）。
     */
    @Select("SELECT COUNT(*) FROM asset_snapshot " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} AND category = #{category} " +
            "AND is_latest = true")
    int countLatestByUserAndDateAndCategory(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate,
            @Param("category") String category);

    /**
     * 1a.8 撤销：把整批 is_latest 翻成 false。
     */
    @Update("UPDATE asset_snapshot SET is_latest = false " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate}")
    int updateIsLatestBySnapshotDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate);

    /**
     * 1a.8 撤销：找 user_id+date < current date 的最近 snapshot 用于恢复。
     */
    @Select("SELECT * FROM asset_snapshot " +
            "WHERE user_id = #{userId} AND snapshot_date < #{beforeDate} " +
            "ORDER BY snapshot_date DESC LIMIT 1")
    AssetSnapshot selectLatestBeforeDate(
            @Param("userId") Long userId,
            @Param("beforeDate") java.time.LocalDate beforeDate);

    /**
     * 1a.4 启动用：列出某 user 最新一版（is_latest=true）的所有 category。
     */
    @Select("SELECT * FROM asset_snapshot " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} AND is_latest = true " +
            "ORDER BY category")
    List<AssetSnapshot> selectLatestByUserAndDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate);
}

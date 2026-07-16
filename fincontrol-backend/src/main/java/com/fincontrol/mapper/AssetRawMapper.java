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
}

package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.FundCategoryMap;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * fund_category_map 表 DAO（[db-schema.sql §3](#)）。
 *
 * <p>继承 {@link BaseMapper} 获得CRUD，附加 1a.3 写入 + 1a.5 查询的自定义方法。
 */
@Mapper
public interface FundCategoryMapMapper extends BaseMapper<FundCategoryMap> {

    /**
     * 1a.3 confirm 写入：UPSERT（user_id, fund_name 唯一索引）。
     *
     * <p>语义：若同 (user_id, fund_name) 已存在则更新（category / source / confirmed_at）；
     * 否则插入新行。
     */
    @Insert("INSERT INTO fund_category_map (user_id, fund_name, category, source, confirmed_at) " +
            "VALUES (#{userId}, #{fundName}, #{category}, #{source}, NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "category = VALUES(category), source = VALUES(source), confirmed_at = NOW()")
    @Options(useGeneratedKeys = true)
    int upsertByFundName(FundCategoryMap map);

    /**
     * 1a.5 暴露给前端：按 fund_name 查单条（GET /api/category-map/match）。
     */
    @Select("SELECT * FROM fund_category_map " +
            "WHERE user_id = #{userId} AND fund_name = #{fundName}")
    FundCategoryMap selectByUserAndFundName(
            @Param("userId") Long userId,
            @Param("fundName") String fundName);

    /**
     * 1a.3 confirm 维度 D 镜像校验：查同 user_id+date 下 fund_name 集合（与 asset_raw 对比）。
     */
    @Select("SELECT DISTINCT fund_name FROM fund_category_map WHERE user_id = #{userId}")
    List<String> selectFundNamesByUser(@Param("userId") Long userId);

    /**
     * 1a.3 confirm 维度 E 检测：同 user_id+date 下 fund_name 集合（用于触发 OVERWRITE_REQUIRED 警告）。
     */
    @Select("SELECT DISTINCT fund_name FROM fund_category_map fcm " +
            "WHERE fcm.user_id = #{userId} " +
            "AND EXISTS (SELECT 1 FROM asset_raw ar " +
            "WHERE ar.user_id = fcm.user_id AND ar.snapshot_date = #{snapshotDate} " +
            "AND ar.fund_name = fcm.fund_name AND ar.is_latest = true)")
    Set<String> selectFundNamesByUserAndSnapshotDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") java.time.LocalDate snapshotDate);
}

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
}

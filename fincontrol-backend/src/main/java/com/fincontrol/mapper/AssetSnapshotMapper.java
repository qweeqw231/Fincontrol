package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.AssetSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * asset_snapshot 表 DAO（[db-schema.sql §2](#)）。
 *
 * <p>继承 {@link BaseMapper} 获得 CRUD。自定义 SQL 在 {@code resources/mapper/AssetSnapshotMapper.xml}
 * 双方言（mysql + h2）实现，详见 {@code mybatis-config.xml} DatabaseIdProvider。
 */
@Mapper
public interface AssetSnapshotMapper extends BaseMapper<AssetSnapshot> {

    /**
     * 1a.3 confirm 写库：UPSERT（user_id, snapshot_date, category, is_latest=true）。
     * <p>实现见 XML — MySQL 用 ON DUPLICATE KEY UPDATE，H2 用 MERGE INTO。
     */
    int upsertByCategory(AssetSnapshot snap);

    /**
     * 维度 D 镜像校验：count is_latest=true 行（同 user_id+date+category 应只 1 行）。
     */
    int countLatestByUserAndDateAndCategory(
            @Param("userId") Long userId,
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("category") String category);

    /**
     * 1a.8 撤销：把整批 is_latest 翻成 false。
     */
    int updateIsLatestBySnapshotDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 1a.8 撤销：找 user_id+date < current date 的最近 snapshot 用于恢复。
     */
    AssetSnapshot selectLatestBeforeDate(
            @Param("userId") Long userId,
            @Param("beforeDate") LocalDate beforeDate);

    /**
     * 1a.4 启动用：列出某 user 最新一版（is_latest=true）的所有 category。
     */
    List<AssetSnapshot> selectLatestByUserAndDate(
            @Param("userId") Long userId,
            @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 1a.4 快照查询：取该 user 最近一次 is_latest=true 的 snapshot_date；没有数据返回 null。
     */
    java.time.LocalDate selectLatestSnapshotDate(@Param("userId") Long userId);
}

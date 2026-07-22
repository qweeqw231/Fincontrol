package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.SnapshotMeta;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * snapshot_meta 表 DAO（决策 27）。
 *
 * <p>每 (user_id, snapshot_date) 唯一一行。
 * 自定义 SQL 见：[src/main/resources/mapper/SnapshotMetaMapper.xml](../../../../resources/mapper/SnapshotMetaMapper.xml)
 *
 * <p>5 个核心方法：
 * <ol>
 *   <li>insert 一行（含 ON DUPLICATE KEY UPDATE 兼容历史数据）</li>
 *   <li>selectByUserAndDate 查 (user, date) 当前状态</li>
 *   <li>selectLatestByUser 查 (user) 所有 is_latest=true 行（per-date 列表）</li>
 *   <li>selectCurrentByUser 查 (user) is_current=true 的快照（仅 1 行）</li>
 *   <li>clearCurrentForUser 翻 (user) 全部 is_current=false（before setCurrent）</li>
 *   <li>setCurrent 翻指定 (user, date) is_current=true</li>
 * </ol>
 */
@org.apache.ibatis.annotations.Mapper
public interface SnapshotMetaMapper extends BaseMapper<SnapshotMeta> {

    /**
     * Step 2：confirm 流程写入 snapshot_meta。
     * <p>UPSERT（user_id, snapshot_date）唯一键：
     * - is_latest 翻 true
     * - is_current 翻 true（每次 confirm 都设为 current）
     * - 旧行 is_latest/is_current 翻 false（confirm 流程前先翻旧）
     */
    int upsertOnConfirm(SnapshotMeta meta);

    /**
     * 翻 (user_id) 全部 is_current=false（setCurrent 前置）
     */
    @Update("UPDATE snapshot_meta SET is_current = false " +
            "WHERE user_id = #{userId} AND is_current = true")
    int clearCurrentForUser(@Param("userId") Long userId);

    /**
     * 翻 (user_id, snapshot_date) is_current=true（setCurrent 主体）
     */
    @Update("UPDATE snapshot_meta SET is_current = true, updated_at = CURRENT_TIMESTAMP " +
            "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate}")
    int setCurrent(@Param("userId") Long userId,
                   @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 翻 (user_id) 全部 is_latest=false 但保留 is_current 不变
     * （用于 confirm 流程前 SnapShotConfirmService 调用，与 asset_raw.is_latest 同步）
     */
    @Update("UPDATE snapshot_meta SET is_latest = false " +
            "WHERE user_id = #{userId} AND is_latest = true")
    int clearLatestForUser(@Param("userId") Long userId);

    /**
     * 查 (user_id, snapshot_date) 当前 meta 行
     */
    @Select("SELECT * FROM snapshot_meta WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate}")
    SnapshotMeta selectByUserAndDate(@Param("userId") Long userId,
                                     @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 查 (user_id) is_current=true 的 snapshotDate（返回值 LocalDate 或 null）
     * <p>Phase 1b.3.4 用：GET /api/snapshot/latest 改查此方法
     */
    @Select("SELECT snapshot_date FROM snapshot_meta " +
            "WHERE user_id = #{userId} AND is_current = true " +
            "ORDER BY snapshot_date DESC LIMIT 1")
    LocalDate selectCurrentDateByUser(@Param("userId") Long userId);

    /**
     * 查 (user_id) 所有 is_latest=true 的 (snapshot_date, is_current) 行
     * <p>Phase 1b.3.4 备用 / Phase 1b.3.9 前端快照管理列表用
     */
    @Select("SELECT * FROM snapshot_meta " +
            "WHERE user_id = #{userId} AND is_latest = true " +
            "ORDER BY snapshot_date DESC")
    List<SnapshotMeta> selectLatestByUser(@Param("userId") Long userId);

    /**
     * 查 (user_id) 全部 is_latest=true 行（per-date 列表，1b.3.9 快照管理用）
     */
    @Select("SELECT * FROM snapshot_meta " +
            "WHERE user_id = #{userId} " +
            "AND is_latest = true " +
            "ORDER BY snapshot_date DESC")
    List<SnapshotMeta> selectAllByUser(@Param("userId") Long userId);
}

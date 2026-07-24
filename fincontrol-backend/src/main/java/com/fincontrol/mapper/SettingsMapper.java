package com.fincontrol.mapper;

import com.fincontrol.entity.Settings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * settings 表 mapper（PR3plus 决策 31）。
 *
 * <p>单表 CRUD：user_id 是主键
 */
@Mapper
public interface SettingsMapper {

    /**
     * 按 user_id 查询。
     * @return Settings 或 null（不存在）
     */
    Settings selectByUserId(@Param("userId") Long userId);

    /**
     * upsert：不存在则 insert，存在则 update max_snapshot_age_days + updated_at。
     * @return 影响行数（1 = inserted，2 = updated）
     */
    int upsert(Settings settings);
}

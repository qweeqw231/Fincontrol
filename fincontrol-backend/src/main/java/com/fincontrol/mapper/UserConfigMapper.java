package com.fincontrol.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 1b.3 补救：user_config 表 key/value 读取。
 * <p>Phase 1 不需要完整 UserConfig 实体；只暴露配置型 + 目标比例等单值查询。
 * <p>用 Map<String,String> resultMap 自动转列：config_key / config_value。
 */
@Mapper
public interface UserConfigMapper {

    /**
     * 查 user_id + config_key 对应值。
     * <p>读取时建议 try-catch + 日志 + fallback（见 UserConfigService）。
     */
    @Select("SELECT config_value FROM user_config " +
            "WHERE user_id = #{userId} AND config_key = #{configKey} " +
            "LIMIT 1")
    String selectValue(@Param("userId") Long userId,
                        @Param("configKey") String configKey);

    /**
     * 一次性返回 user_id 下所有 key→value，用于解析 target_ratios 等 JSON 配置。
     */
    @Select("SELECT config_key AS configKey, config_value AS configValue " +
            "FROM user_config WHERE user_id = #{userId}")
    java.util.List<Map<String, String>> selectAllByUser(@Param("userId") Long userId);
}

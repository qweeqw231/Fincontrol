package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * operation_log 表 DAO（Phase 3 事件时间线）。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Select("SELECT * FROM operation_log WHERE user_id = #{userId} " +
            "ORDER BY operation_date ASC, id ASC")
    List<OperationLog> selectAllByUser(@Param("userId") Long userId);
}
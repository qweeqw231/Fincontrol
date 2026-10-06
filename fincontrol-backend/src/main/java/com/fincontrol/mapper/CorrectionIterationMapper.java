package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.CorrectionIteration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * correction_iteration 表 DAO（2a 校正页 · 迭代轮次明细）。
 */
@Mapper
public interface CorrectionIterationMapper extends BaseMapper<CorrectionIteration> {

    @Select("SELECT * FROM correction_iteration WHERE operation_log_id = #{operationLogId} " +
            "ORDER BY sort_order ASC, id ASC")
    List<CorrectionIteration> selectByOperationLog(@Param("operationLogId") Long operationLogId);
}
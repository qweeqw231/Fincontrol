package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.CorrectionParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * correction_param 表 DAO（2a 校正页 · 参数与指标 key-value）。
 */
@Mapper
public interface CorrectionParamMapper extends BaseMapper<CorrectionParam> {

    @Select("SELECT * FROM correction_param WHERE operation_log_id = #{operationLogId} " +
            "ORDER BY id ASC")
    List<CorrectionParam> selectByOperationLog(@Param("operationLogId") Long operationLogId);

    /** 列表摘要用：一次性取某 user 全部参数行（避免按记录 N+1）。 */
    @Select("SELECT * FROM correction_param WHERE user_id = #{userId} " +
            "ORDER BY operation_log_id ASC, id ASC")
    List<CorrectionParam> selectByUser(@Param("userId") Long userId);
}
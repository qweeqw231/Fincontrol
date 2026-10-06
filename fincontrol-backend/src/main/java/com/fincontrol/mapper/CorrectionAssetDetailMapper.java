package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.CorrectionAssetDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * correction_asset_detail 表 DAO（2a 校正页 · 逐资产明细）。
 */
@Mapper
public interface CorrectionAssetDetailMapper extends BaseMapper<CorrectionAssetDetail> {

    @Select("SELECT * FROM correction_asset_detail WHERE operation_log_id = #{operationLogId} " +
            "ORDER BY sort_order ASC, id ASC")
    List<CorrectionAssetDetail> selectByOperationLog(@Param("operationLogId") Long operationLogId);
}
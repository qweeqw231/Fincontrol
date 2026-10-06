package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 校正参数与指标表（2a 校正页，[db-schema.sql §7.3](#)）。
 *
 * <p>key-value 存放不适合建列的参数与指标，便于新增指标不改表。
 * <p>常用 param_key 枚举：surplus/mMax/mMaxSource/alphaInit/alphaDecay/alphaFinal/
 * zohThreshold/zohStepPointEHigh/zohStepTotal/icDrrBefore/icDrrAfter/icDrrPct/
 * anchor/zohTriggered/actualTransfer/kktNote。
 */
@Data
@NoArgsConstructor
@TableName("correction_param")
public class CorrectionParam {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("operation_log_id")
    private Long operationLogId;

    @TableField("param_key")
    private String paramKey;

    @TableField("num_value")
    private BigDecimal numValue;

    @TableField("text_value")
    private String textValue;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
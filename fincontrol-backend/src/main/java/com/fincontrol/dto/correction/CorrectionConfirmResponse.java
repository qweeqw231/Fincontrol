package com.fincontrol.dto.correction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 2a 校正确认入库响应（月度/季度共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionConfirmResponse {

    /** 写入的 operation_log.id */
    private Long operationLogId;

    /** 操作时间 */
    private String operationDate;

    /** 恒为 true（写入 operation_log）；预留失败标识 */
    private Boolean writtenToOperationLog;

    /** 明细行数（月度通常为 0；季度含迭代/资产/参数） */
    private Integer detailRows;
}
package com.fincontrol.dto.screenshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视觉模型解析失败时响应 data 子结构（[api-contract.md §2.2 失败分支](#)）。
 *
 * <p>替代旧 DeepSeekErrorData（Phase 1a.5+）。错误码 3001/3002/3003 字段名保持稳定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VisionErrorData {
    private String conversationId;
    /** parse_failed | zero_funds | timeout */
    private String errorType;
    private String aiRawResponse;
}

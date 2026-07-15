package com.fincontrol.dto.screenshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析失败时响应 data 子结构（[api-contract.md §2.2 失败分支](#)）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepSeekErrorData {
    private String conversationId;
    /** parse_failed | zero_funds | timeout */
    private String errorType;
    private String aiRawResponse;
}

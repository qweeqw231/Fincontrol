package com.fincontrol.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应结构（来自 [api-contract.md §1.3](#)）。
 *
 * <pre>
 * 成功：{ "code": 0, "message": "success", "data": { ... } }
 * 失败：{ "code": 3001, "message": "DeepSeek API 返回非 JSON", "data": null }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public ApiResponse() {}

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}

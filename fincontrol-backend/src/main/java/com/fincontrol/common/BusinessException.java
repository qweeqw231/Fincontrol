package com.fincontrol.common;

/**
 * 业务异常：携带语义化错误码。
 * 由 {@link GlobalExceptionHandler} 统一转成 {@link ApiResponse}。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Object getData() { return data; }
}

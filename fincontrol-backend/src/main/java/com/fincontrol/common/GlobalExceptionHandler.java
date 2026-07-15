package com.fincontrol.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：业务异常 → 携带 errorCode 的 ApiResponse；
 * 校验异常 → 1001；上传过大 → 1003；其他 → 5001。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        log.warn("BusinessException code={} msg={}", ex.getErrorCode().getCode(), ex.getMessage());
        HttpStatus status = HttpStatusCode.forErrorCode(ex.getErrorCode().getCode());
        ApiResponse<Object> body = ex.getData() == null
                ? ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage())
                : new ApiResponse<>(ex.getErrorCode().getCode(), ex.getMessage(), ex.getData());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("参数无效");
        return ResponseEntity.badRequest().body(ApiResponse.error(1001, msg));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(1003, "文件超过最大限制: " + ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(1001, ex.getMessage()));
    }

    @ExceptionHandler({JsonProcessingException.class, JsonMappingException.class})
    public ResponseEntity<ApiResponse<Object>> handleJson(Exception ex) {
        log.warn("JSON parse error {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(3001, "DeepSeek API 返回非 JSON：" + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAny(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(5001, "服务器内部错误: " + ex.getClass().getSimpleName()));
    }

    /**
     * 错误码到 HTTP 状态的映射。
     */
    private static final class HttpStatusCode {
        static HttpStatus forErrorCode(int code) {
            if (code >= 1000 && code < 1100) return HttpStatus.BAD_REQUEST;
            if (code == 2001) return HttpStatus.NOT_FOUND;
            if (code == 2002) return HttpStatus.CONFLICT;
            if (code == 2003) return HttpStatus.GONE;
            if (code == 3001) return HttpStatus.BAD_GATEWAY;
            if (code == 3002) return HttpStatus.GATEWAY_TIMEOUT;
            if (code == 3003) return HttpStatus.BAD_GATEWAY;
            if (code >= 3000 && code < 3100) return HttpStatus.BAD_GATEWAY;
            if (code >= 5000) return HttpStatus.INTERNAL_SERVER_ERROR;
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}

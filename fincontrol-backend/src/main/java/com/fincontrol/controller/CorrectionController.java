package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.correction.CorrectionConfirmResponse;
import com.fincontrol.dto.correction.CorrectionDefaultsResponse;
import com.fincontrol.dto.correction.MonthlyCalculateRequest;
import com.fincontrol.dto.correction.MonthlyCalculateResponse;
import com.fincontrol.dto.correction.MonthlyConfirmRequest;
import com.fincontrol.dto.correction.MonthlyRecalculateRequest;
import com.fincontrol.dto.correction.MonthlyRecalculateResponse;
import com.fincontrol.dto.correction.QuarterlyCalculateRequest;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse;
import com.fincontrol.dto.correction.QuarterlyConfirmRequest;
import com.fincontrol.dto.correction.QuarterlyDefaultsResponse;
import com.fincontrol.service.CorrectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 2a 校正页 API（月度操作台 + 季度 LQR-ZOH 操作台 + 校正记录回放）。
 *
 * <p>GET  /api/correction/defaults              — 月度台默认值
 * <br>POST /api/correction/monthly/calculate     — 低波方程组求解
 * <br>POST /api/correction/monthly/recalculate   — 取整弹窗实时重算
 * <br>POST /api/correction/monthly/confirm       — 确认写 operation_log
 * <br>GET  /api/correction/quarterly/defaults    — 季度台默认值
 * <br>POST /api/correction/quarterly/calculate   — LQR-ZOH 联合求解
 * <br>POST /api/correction/quarterly/confirm     — 确认写 4 张表
 * <br>GET  /api/correction/operations            — 校正与操作记录列表
 * <br>GET  /api/correction/operations/{id}       — 单条记录完整明细（回放）
 */
@RestController
@RequestMapping("/api")
public class CorrectionController {

    private static final Logger log = LoggerFactory.getLogger(CorrectionController.class);

    private final CorrectionService correctionService;

    public CorrectionController(CorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    // ---------------- 月度台 ----------------

    @GetMapping("/correction/defaults")
    public ApiResponse<CorrectionDefaultsResponse> monthlyDefaults(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/correction/defaults: userId={}", userId);
        return ApiResponse.success(correctionService.getMonthlyDefaults(userId));
    }

    @PostMapping("/correction/monthly/calculate")
    public ApiResponse<MonthlyCalculateResponse> monthlyCalculate(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody MonthlyCalculateRequest req) {
        log.info("POST /api/correction/monthly/calculate: userId={}", userId);
        return ApiResponse.success(correctionService.calculateMonthly(userId, req));
    }

    @PostMapping("/correction/monthly/recalculate")
    public ApiResponse<MonthlyRecalculateResponse> monthlyRecalculate(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody MonthlyRecalculateRequest req) {
        log.info("POST /api/correction/monthly/recalculate: userId={}", userId);
        return ApiResponse.success(correctionService.recalculateMonthly(userId, req));
    }

    @PostMapping("/correction/monthly/confirm")
    public ApiResponse<CorrectionConfirmResponse> monthlyConfirm(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody MonthlyConfirmRequest req) {
        log.info("POST /api/correction/monthly/confirm: userId={} snapshotDate={}",
                userId, req.getSnapshotDate());
        return ApiResponse.success(correctionService.confirmMonthly(userId, req));
    }

    // ---------------- 季度台 ----------------

    @GetMapping("/correction/quarterly/defaults")
    public ApiResponse<QuarterlyDefaultsResponse> quarterlyDefaults(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/correction/quarterly/defaults: userId={}", userId);
        return ApiResponse.success(correctionService.getQuarterlyDefaults(userId));
    }

    @PostMapping("/correction/quarterly/calculate")
    public ApiResponse<QuarterlyCalculateResponse> quarterlyCalculate(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody QuarterlyCalculateRequest req) {
        log.info("POST /api/correction/quarterly/calculate: userId={} mode={}", userId, req.getMode());
        return ApiResponse.success(correctionService.calculateQuarterly(userId, req));
    }

    @PostMapping("/correction/quarterly/confirm")
    public ApiResponse<CorrectionConfirmResponse> quarterlyConfirm(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody QuarterlyConfirmRequest req) {
        log.info("POST /api/correction/quarterly/confirm: userId={} snapshotDate={}",
                userId, req.getSnapshotDate());
        return ApiResponse.success(correctionService.confirmQuarterly(userId, req));
    }

    // ---------------- 记录列表与详情 ----------------

    @GetMapping("/correction/operations")
    public ApiResponse<Map<String, Object>> operations(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/correction/operations: userId={}", userId);
        return ApiResponse.success(correctionService.listOperations(userId));
    }

    @GetMapping("/correction/operations/{id}")
    public ApiResponse<Map<String, Object>> operationDetail(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @PathVariable("id") Long id) {
        log.info("GET /api/correction/operations/{}: userId={}", id, userId);
        return ApiResponse.success(correctionService.getOperationDetail(userId, id));
    }
}
package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.service.NavQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phase 3：净值曲线 + 比例时间线 API。
 *
 * <p>GET /api/nav/history    — 每日净值 + 累计收益 + 里程碑
 * <br>GET /api/nav/operations — 校正与操作记录（ZOH/LQR + 战术调仓）
 * <br>GET /api/ratio/history — 六大类占比时间线
 */
@RestController
@RequestMapping("/api")
public class NavController {

    private static final Logger log = LoggerFactory.getLogger(NavController.class);

    private final NavQueryService navQueryService;

    public NavController(NavQueryService navQueryService) {
        this.navQueryService = navQueryService;
    }

    @GetMapping("/nav/history")
    public ApiResponse<Map<String, Object>> navHistory(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/nav/history: userId={}", userId);
        return ApiResponse.success(navQueryService.getNavHistory(userId));
    }

    @GetMapping("/ratio/history")
    public ApiResponse<Map<String, Object>> ratioHistory(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/ratio/history: userId={}", userId);
        return ApiResponse.success(navQueryService.getRatioHistory(userId));
    }

    @GetMapping("/nav/operations")
    public ApiResponse<Map<String, Object>> navOperations(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/nav/operations: userId={}", userId);
        return ApiResponse.success(navQueryService.getOperations(userId));
    }
}

package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.nav.NavStatisticsResponse;
import com.fincontrol.service.NavQueryService;
import com.fincontrol.service.NavStatisticsService;
import com.fincontrol.service.NavSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phase 3 / 2b：净值与绩效统计 API。
 *
 * <p>GET /api/nav/history    — 每日净值 + 累计收益 + 里程碑
 * <br>GET /api/nav/operations — 校正与操作记录（ZOH/LQR + 战术调仓）
 * <br>GET /api/ratio/history — 六大类占比时间线
 * <br>GET /api/nav/statistics   — 绩效统计（2b：核心绩效/风险调整/分布与区间）
 * <br>GET /api/nav/sync-status  — Excel 自动同步状态
 * <br>POST /api/nav/sync        — 手动触发 Excel 同步
 */
@RestController
@RequestMapping("/api")
public class NavController {

    private static final Logger log = LoggerFactory.getLogger(NavController.class);

    private final NavQueryService navQueryService;
    private final NavStatisticsService navStatisticsService;
    private final NavSyncService navSyncService;

    public NavController(NavQueryService navQueryService,
                         NavStatisticsService navStatisticsService,
                         NavSyncService navSyncService) {
        this.navQueryService = navQueryService;
        this.navStatisticsService = navStatisticsService;
        this.navSyncService = navSyncService;
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

    /** 2b：绩效统计（核心绩效 / 风险调整 / 分布与区间）。 */
    @GetMapping("/nav/statistics")
    public ApiResponse<NavStatisticsResponse> navStatistics(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/nav/statistics: userId={}", userId);
        return ApiResponse.success(navStatisticsService.compute(userId));
    }

    /** 2b：Excel 自动同步状态。 */
    @GetMapping("/nav/sync-status")
    public ApiResponse<Map<String, Object>> navSyncStatus(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("GET /api/nav/sync-status: userId={}", userId);
        return ApiResponse.success(navSyncService.status());
    }

    /** 2b：手动触发 Excel 同步（自动轮询的兜底入口）。 */
    @PostMapping("/nav/sync")
    public ApiResponse<Map<String, Object>> navSync(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("POST /api/nav/sync: userId={}", userId);
        return ApiResponse.success(navSyncService.syncNow());
    }
}

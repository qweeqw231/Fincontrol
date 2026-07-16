package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.asset.AssetBalanceResponse;
import com.fincontrol.dto.asset.OperationsRecentResponse;
import com.fincontrol.service.AssetQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 1a.4 首页辅助 API（[api-contract.md §9](#)）。
 *
 * <p>GET /api/asset/balance                — 余额类卡片（A4-S06）
 * <br>GET /api/asset/operations/recent    — 最近解析活动（A4-S07）
 * <br>GET /api/asset/cumulative-return    — 累计收益率占位（A4-S08）
 *
 * <p>错误码由 {@code GlobalExceptionHandler} 统一映射。
 */
@RestController
@RequestMapping("/api/asset")
public class AssetController {

    private static final Logger log = LoggerFactory.getLogger(AssetController.class);

    private final AssetQueryService assetQueryService;

    public AssetController(AssetQueryService assetQueryService) {
        this.assetQueryService = assetQueryService;
    }

    /**
     * 1a.4 余额类卡片数据（[api-contract.md §9.1](#)）。
     */
    @GetMapping("/balance")
    public ApiResponse<AssetBalanceResponse> balance(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId) {
        log.info("1a.4 balance: userId={}", userId);
        return ApiResponse.success(assetQueryService.getBalance(userId));
    }

    /**
     * 1a.4 最近解析活动（[api-contract.md §9.2](#)）。
     */
    @GetMapping("/operations/recent")
    public ApiResponse<OperationsRecentResponse> operationsRecent(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "5") int limit) {
        log.info("1a.4 operations/recent: userId={} limit={}", userId, limit);
        return ApiResponse.success(assetQueryService.getRecentOperations(userId, limit));
    }

    /**
     * 1a.4 累计收益率占位（[api-contract.md §9.3](#)）。
     * <p>Phase 1 固定返回 {@code available:false}；Phase 3 再实现算法。
     */
    @GetMapping("/cumulative-return")
    public ApiResponse<Map<String, Object>> cumulativeReturn() {
        log.info("1a.4 cumulative-return: returning phase1 placeholder");
        AssetQueryService.CumulativeReturnPlaceholder body = assetQueryService.getCumulativeReturnPlaceholder();
        Map<String, Object> data = new HashMap<>();
        data.put("available", body.available());
        data.put("message", body.message());
        return ApiResponse.success(data);
    }
}

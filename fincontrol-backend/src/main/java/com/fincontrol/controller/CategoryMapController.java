package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.category.CategoryMapMatchResponse;
import com.fincontrol.dto.category.CategoryMapUpdateRequest;
import com.fincontrol.dto.category.CategoryMapUpdateResponse;
import com.fincontrol.entity.FundCategoryMap;
import com.fincontrol.service.CategoryMapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1a.5 + 1a.8.8 大类映射 API（[api-contract.md §7](#)）。
 *
 * <p>端点：
 * <ul>
 *   <li>GET  /api/category-map/match?userId=N&funds=...        批量查询（1a.16，[api-contract.md §7.1](#)）</li>
 *   <li>POST /api/category-map/update                             单条更新（1a.17，[api-contract.md §7.2](#)）</li>
 *   <li>DELETE /api/category-map/{userId}/{fundName}              删除单条（1a.8.8）</li>
 *   <li>POST /api/category-map/reset                              重置为 ai_guess（1a.8.8）</li>
 *   <li>GET  /api/category-map/stale?userId=N&days=N           列 stale（1a.8.8）</li>
 * </ul>
 *
 * <p>userId 来源：{@code X-User-Id} header（默认 1）；{@code ?userId=N} query param（1a.8.8 显式覆盖）。
 *
 * <p>错误码由 {@code GlobalExceptionHandler} 统一映射（1002/1004 → 400 等）。
 */
@RestController
@RequestMapping("/api/category-map")
public class CategoryMapController {

    private static final Logger log = LoggerFactory.getLogger(CategoryMapController.class);

    /** 1a.8.8 默认 stale 阈值（[决策 8]）。 */
    private static final int DEFAULT_STALE_DAYS = 90;

    private final CategoryMapService categoryMapService;

    public CategoryMapController(CategoryMapService categoryMapService) {
        this.categoryMapService = categoryMapService;
    }

    /**
     * 1a.16 批量查询映射（[api-contract.md §7.1](#)）。
     *
     * <p>1a.8.8 增强：支持 {@code ?userId=N} 显式覆盖 header（多用户债修复）。
     */
    @GetMapping("/match")
    public ApiResponse<CategoryMapMatchResponse> match(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long headerUserId,
            @RequestParam(name = "userId", required = false) Long queryUserId,
            @RequestParam(name = "funds", required = false) String funds) {
        Long userId = queryUserId != null ? queryUserId : headerUserId;
        log.info("1a.5 match: userId={} (header={} query={}) funds.len={}",
                userId, headerUserId, queryUserId, funds == null ? 0 : funds.length());
        return ApiResponse.success(categoryMapService.match(userId, funds));
    }

    /**
     * 1a.17 更新单条映射（[api-contract.md §7.2](#)，[P0-1.3]）。
     *
     * <p>1a.8.8 增强：显式支持 body.userId 覆盖 header。
     */
    @PostMapping("/update")
    public ApiResponse<CategoryMapUpdateResponse> update(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long headerUserId,
            @RequestBody(required = false) CategoryMapUpdateRequest req) {
        if (req == null) {
            req = new CategoryMapUpdateRequest();
        }
        Long userId = req.getUserId() != null ? req.getUserId() : headerUserId;
        log.info("1a.5 update: userId={} (header={}) fund={} category={}",
                userId, headerUserId, req.getFundName(), req.getCategory());
        return ApiResponse.success(
                categoryMapService.update(userId, req.getFundName(), req.getCategory()));
    }

    /**
     * 1a.8.8 DELETE：删除单条映射。URL 路径带 userId 防误删；X-User-Id 不一致以 URL 为准。
     */
    @DeleteMapping("/{userId}/{fundName}")
    public ApiResponse<Map<String, Object>> delete(
            @PathVariable("userId") Long userId,
            @PathVariable("fundName") String fundName) {
        int affected = categoryMapService.delete(userId, fundName);
        log.info("1a.8.8 delete: userId={} fund={} affected={}", userId, fundName, affected);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("fundName", fundName);
        data.put("affected", affected);
        return ApiResponse.success(data);
    }

    /**
     * 1a.8.8 reset：重置 source 为 ai_guess（保留 category 行）。
     */
    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long headerUserId,
            @RequestParam(name = "userId", required = false) Long queryUserId,
            @RequestParam(name = "fundName", required = false) String fundName) {
        Long userId = queryUserId != null ? queryUserId : headerUserId;
        int affected = categoryMapService.reset(userId, fundName);
        log.info("1a.8.8 reset: userId={} fund={} affected={}", userId, fundName, affected);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("fundName", fundName);
        data.put("affected", affected);
        data.put("source", "ai_guess");
        return ApiResponse.success(data);
    }

    /**
     * 1a.8.8 stale：列 last_seen_at 早于 now-days 的 user_correct 映射（默认 90 天）。
     */
    @GetMapping("/stale")
    public ApiResponse<List<FundCategoryMap>> stale(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long headerUserId,
            @RequestParam(name = "userId", required = false) Long queryUserId,
            @RequestParam(name = "days", required = false, defaultValue = "90") Integer days) {
        Long userId = queryUserId != null ? queryUserId : headerUserId;
        int safeDays = days == null ? DEFAULT_STALE_DAYS : Math.max(0, days);
        log.info("1a.8.8 stale: userId={} days={}", userId, safeDays);
        return ApiResponse.success(categoryMapService.listStale(userId, safeDays));
    }
}
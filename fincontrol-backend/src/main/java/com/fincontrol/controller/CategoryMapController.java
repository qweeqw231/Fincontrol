package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.category.CategoryMapMatchResponse;
import com.fincontrol.dto.category.CategoryMapUpdateRequest;
import com.fincontrol.dto.category.CategoryMapUpdateResponse;
import com.fincontrol.service.CategoryMapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1a.5 大类映射 API（[api-contract.md §7](#)）。
 *
 * <p>GET /api/category-map/match    — 批量查询映射（[api-contract.md §7.1](#)，1a.16）
 * <br>POST /api/category-map/update — 更新单条映射（[api-contract.md §7.2](#)，1a.17 / [P0-1.3]）
 *
 * <p>userId 来源：{@code X-User-Id} header（默认 1）；{@code update} 请求体 {@code userId} 字段
 * 若提供则覆盖 header，便于压测 / 多用户场景。
 *
 * <p>错误码由 {@code GlobalExceptionHandler} 统一映射（1002/1004 → 400 等）。
 */
@RestController
@RequestMapping("/api/category-map")
public class CategoryMapController {

    private static final Logger log = LoggerFactory.getLogger(CategoryMapController.class);

    private final CategoryMapService categoryMapService;

    public CategoryMapController(CategoryMapService categoryMapService) {
        this.categoryMapService = categoryMapService;
    }

    /**
     * 1a.16 批量查询映射（[api-contract.md §7.1](#)）。
     *
     * @param funds 逗号分隔基金名（最多 50 个唯一值）
     */
    @GetMapping("/match")
    public ApiResponse<CategoryMapMatchResponse> match(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestParam(name = "funds", required = false) String funds) {
        log.info("1a.5 match: userId={} funds.len={}", userId, funds == null ? 0 : funds.length());
        return ApiResponse.success(categoryMapService.match(userId, funds));
    }

    /**
     * 1a.17 更新单条映射（[api-contract.md §7.2](#)，[P0-1.3]）。
     */
    @PostMapping("/update")
    public ApiResponse<CategoryMapUpdateResponse> update(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long headerUserId,
            @RequestBody CategoryMapUpdateRequest req) {
        if (req == null) {
            req = new CategoryMapUpdateRequest();
        }
        Long userId = req.getUserId() != null ? req.getUserId() : headerUserId;
        log.info("1a.5 update: userId={} fund={} category={}",
                userId, req.getFundName(), req.getCategory());
        return ApiResponse.success(
                categoryMapService.update(userId, req.getFundName(), req.getCategory()));
    }
}
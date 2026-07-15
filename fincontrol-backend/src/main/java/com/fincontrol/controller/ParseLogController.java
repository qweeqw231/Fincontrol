package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.screenshot.ParseLogItem;
import com.fincontrol.service.ParseLogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 10.1 GET /api/parse-logs（Phase 1a.2 / 1a.23）。
 *
 * <p>Phase 1 简版：派生于 chat_history；上限 100 条，由 limit 控制（默认 50）。
 * 1b 前端解析日志侧边栏（10.1）调用此端点。
 */
@RestController
@RequestMapping("/api/parse-logs")
public class ParseLogController {

    private final ParseLogQueryService parseLogQueryService;

    public ParseLogController(ParseLogQueryService parseLogQueryService) {
        this.parseLogQueryService = parseLogQueryService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<ParseLogItem> items = parseLogQueryService.listLatest(safeLimit);
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("page", 1);
        data.put("pageSize", safeLimit);
        return ApiResponse.success(data);
    }
}

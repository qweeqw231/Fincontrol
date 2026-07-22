package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.screenshot.*;
import com.fincontrol.service.ScreenshotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Screenshot 4 端点（Phase 1a.2 + 1a.10）：
 * <ul>
 *   <li>{@code POST /api/screenshot/upload}  —— 1a.4（仅存盘）</li>
 *   <li>{@code POST /api/screenshot/parse}   —— 1a.5（含 [P0-1.4] 三类失败码）</li>
 *   <li>{@code POST /api/screenshot/reparse} —— 1a.6（[P0-4.4]）</li>
 *   <li>{@code POST /api/screenshot/parse-batch} —— 1a.10（单次有序多图）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/screenshot")
public class ScreenshotController {

    private final ScreenshotService screenshotService;

    public ScreenshotController(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<ScreenshotUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(screenshotService.upload(file));
    }

    @PostMapping("/parse")
    public ApiResponse<ParsedAsset> parse(@Valid @RequestBody ScreenshotParseRequest req) {
        return ApiResponse.success(screenshotService.parse(req));
    }

    @PostMapping("/reparse")
    public ApiResponse<ParsedAsset> reparse(@Valid @RequestBody ScreenshotReparseRequest req) {
        return ApiResponse.success(screenshotService.reparse(req));
    }

    /**
     * parseBatch：支持 2 种模式（决策 26，2026-07-22）
     * <p>{@code mode=single}（默认）：fileIds 串行单图 parse，失败 1 次重试。历史测试证明单图比多图 batch 更稳定（1a.10 决策 12 后多图 4 图 batch minimax 易超时）。
     * <p>{@code mode=multi}：调 AiRouter.callVision() 走原 4 图 batch。
     *
     * @param mode 'single' | 'multi'，默认 'single'
     */
    @PostMapping(value = "/parse-batch", params = "mode")
    public ApiResponse<ScreenshotBatchParseResponse> parseBatchWithMode(
            @RequestParam(value = "mode", defaultValue = "single") String mode,
            @Valid @RequestBody ScreenshotBatchParseRequest req) {
        return ApiResponse.success(screenshotService.parseBatch(req, mode));
    }

    /** parseBatch 默认模式（向后兼容：mode=single） */
    @PostMapping("/parse-batch")
    public ApiResponse<ScreenshotBatchParseResponse> parseBatch(
            @Valid @RequestBody ScreenshotBatchParseRequest req) {
        return ApiResponse.success(screenshotService.parseBatch(req, "single"));
    }
}

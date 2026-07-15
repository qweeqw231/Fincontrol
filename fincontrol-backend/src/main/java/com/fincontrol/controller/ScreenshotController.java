package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.screenshot.*;
import com.fincontrol.service.ScreenshotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Screenshot 3 端点（Phase 1a.2）：
 * <ul>
 *   <li>{@code POST /api/screenshot/upload}  —— 1a.4（仅存盘）</li>
 *   <li>{@code POST /api/screenshot/parse}   —— 1a.5（含 [P0-1.4] 三类失败码）</li>
 *   <li>{@code POST /api/screenshot/reparse} —— 1a.6（[P0-4.4]）</li>
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
}

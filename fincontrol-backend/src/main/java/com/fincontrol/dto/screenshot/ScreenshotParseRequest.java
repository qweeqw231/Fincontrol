package com.fincontrol.dto.screenshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 2.2 POST /api/screenshot/parse 请求体（[api-contract.md §2.2](#)）。
 *
 * <p>2026-07-20 决策 13：新增 {@link #dataTime} 字段（可选 LocalDate），
 * 优先级 = 前端用户输入 > AI 提取 > LocalDate.now()。
 * 前端从 EXIF DateTimeOriginal 提取或手动选日期透传。
 */
@Data
public class ScreenshotParseRequest {

    @NotBlank(message = "fileId 必填")
    private String fileId;

    @NotNull(message = "userId 必填")
    private Long userId;

    /** 决策 13：截图对应的实际数据日期（前端从 EXIF 读或手动选）；null = 走 AI 提取 fallback。 */
    private LocalDate dataTime;
}

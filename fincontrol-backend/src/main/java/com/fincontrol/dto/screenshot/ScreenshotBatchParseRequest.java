package com.fincontrol.dto.screenshot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 1a.10 POST /api/screenshot/parse-batch 请求。
 *
 * <p>2026-07-20 决策 13：新增 {@link #dataTime} 字段（可选 LocalDate），
 * 优先级 = 前端用户输入 > AI 提取 > LocalDate.now()。
 * 4 张图共享同一 dataTime（多图属于同一时点同一账户）。
 */
@Data
public class ScreenshotBatchParseRequest {

    @NotNull(message = "userId 必填")
    private Long userId;

    @NotEmpty(message = "fileIds 至少包含 1 个 fileId")
    @Size(max = 10, message = "fileIds 最多 10 个")
    @Valid
    private List<@NotBlank(message = "fileId 不能为空") String> fileIds;

    /** 决策 13：4 张图对应的实际数据日期（同一时点同一账户）。null = 走 AI 提取 fallback。 */
    private LocalDate dataTime;
}

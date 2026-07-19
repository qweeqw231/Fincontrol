package com.fincontrol.dto.screenshot;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 2.3 POST /api/screenshot/reparse 请求体（[api-contract.md §2.3](#)）。
 *
 * <p>1a.10 决策 13：新增 {@link #dataTime} 字段，可选。
 * <ul>
 *   <li>如果传 dataTime：reparse 后用 dataTime 覆盖 AI 提取的 snapshot_date</li>
 *   <li>如果为 null：保持 AI 解析值（不改）</li>
 * </ul>
 */
@Data
public class ScreenshotReparseRequest {

    @NotBlank(message = "conversationId 必填")
    private String conversationId;

    /**
     * 决策 13：截图对应的真实数据日期（可选；前端从 EXIF 读或手动选）。
     * <p>如果为 null，reparse 保持 AI 解析的 snapshot_date（fallback）。
     */
    private LocalDate dataTime;
}

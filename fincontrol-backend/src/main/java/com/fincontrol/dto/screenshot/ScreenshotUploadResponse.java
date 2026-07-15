package com.fincontrol.dto.screenshot;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 2.1 POST /api/screenshot/upload 响应（[api-contract.md §2.1](#)）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenshotUploadResponse {
    private String fileId;
    private String fileUrl;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime uploadedAt;
}

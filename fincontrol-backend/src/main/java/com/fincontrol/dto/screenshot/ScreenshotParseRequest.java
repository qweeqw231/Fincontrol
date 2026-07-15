package com.fincontrol.dto.screenshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 2.2 POST /api/screenshot/parse 请求体（[api-contract.md §2.2](#)）。
 */
@Data
public class ScreenshotParseRequest {

    @NotBlank(message = "fileId 必填")
    private String fileId;

    @NotNull(message = "userId 必填")
    private Long userId;
}

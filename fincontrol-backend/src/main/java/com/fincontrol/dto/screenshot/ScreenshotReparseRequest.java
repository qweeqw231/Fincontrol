package com.fincontrol.dto.screenshot;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 2.3 POST /api/screenshot/reparse 请求体（[api-contract.md §2.3](#)）。
 */
@Data
public class ScreenshotReparseRequest {

    @NotBlank(message = "conversationId 必填")
    private String conversationId;
}

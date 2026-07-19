package com.fincontrol.dto.screenshot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 1a.10 POST /api/screenshot/parse-batch 请求。 */
@Data
public class ScreenshotBatchParseRequest {

    @NotNull(message = "userId 必填")
    private Long userId;

    @NotEmpty(message = "fileIds 至少包含 1 个 fileId")
    @Size(max = 10, message = "fileIds 最多 10 个")
    @Valid
    private List<@NotBlank(message = "fileId 不能为空") String> fileIds;
}

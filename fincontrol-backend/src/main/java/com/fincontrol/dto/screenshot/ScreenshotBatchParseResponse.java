package com.fincontrol.dto.screenshot;

import com.fincontrol.service.DedupEngine.DedupReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 1a.10 单次多图解析响应：合并资产 + 去重/加和审计信息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenshotBatchParseResponse {
    private ParsedAsset parsedAsset;
    private DedupReport dedupReport;
    private String usedProvider;
    private boolean fallbackTriggered;
    private boolean cacheHit;
    private int imageCount;
}

package com.fincontrol.dto.screenshot;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 10.1 GET /api/parse-logs 响应 items 元素（[api-contract.md §10.1](#)）。
 *
 * <p>Phase 1：派生自 chat_history（logId=id）；status 推断逻辑见 ParseLogQueryService。
 * Phase 2：operation_log 上线后改为跨表查询（[decisions.md 决策 C 触发连锁](#)）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParseLogItem {
    private Long logId;
    private String conversationId;
    private LocalDate snapshotDate;
    private int fundCount;
    /** imported | parse_failed | pending */
    private String status;
    /** screenshot_manual | ai_resolve | manual_input ... */
    private String source;

    /**
     * 1b.3 补救 R2：解析失败时的可读错误信息（仅在 status=parse_failed 或无可恢复 JSON 时设置）。
     * <p>前端可直接拼接到 summary，避免再调用详细错误。
     */
    private String parseError;

    /**
     * 1b.3 补救 R2：各大类基金数（canonical name → count）。
     * <p>仅在成功解析 categories 时填充；解析失败/无法恢复时为 null。
     */
    private Map<String, Integer> fundCountByCategory;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime confirmedAt;
}

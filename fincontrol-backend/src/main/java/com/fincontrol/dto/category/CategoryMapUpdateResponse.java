package com.fincontrol.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 1a.5 更新单条映射的响应体（[api-contract.md §7.2](#)）。
 *
 * <p>{@code updated} 标识本次写库是 UPDATE（true）还是 INSERT（false）：
 * <ul>
 *   <li>true — 已存在映射，本次为 UPDATE；{@code source} 固定 {@code user_correct}</li>
 *   <li>false — 全新映射，本次为 INSERT；{@code source} 固定 {@code user_manual}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMapUpdateResponse {

    private Long mappingId;

    private String fundName;

    private String category;

    /** user_correct / user_manual */
    private String source;

    private LocalDateTime confirmedAt;

    /** true = UPDATE 路径；false = INSERT 路径 */
    private boolean updated;
}
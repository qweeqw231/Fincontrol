package com.fincontrol.dto.category;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1a.5 更新单条映射的请求体（[api-contract.md §7.2](#)）。
 *
 * <p>{@code userId} 可选；缺省时 Controller fallback 到 {@code X-User-Id} header（默认 1）。
 * <br>{@code source} 字段被 1a.5 服务端忽略 — UPDATE 路径固定返回 {@code user_correct}，
 * INSERT 路径固定返回 {@code user_manual}（[P0-1.3] 锁定规则）。
 */
@Data
@NoArgsConstructor
public class CategoryMapUpdateRequest {

    private String fundName;

    private String category;

    /** 请求体可携带 userId；缺省走 header，便于压测与多用户场景。 */
    private Long userId;
}
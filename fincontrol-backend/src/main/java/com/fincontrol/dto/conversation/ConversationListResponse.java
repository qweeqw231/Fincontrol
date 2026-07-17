package com.fincontrol.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 1a.19 /api/conversations 列表响应（[api-contract.md §8.2](#)）。
 *
 * <p>Phase 1 简化版：分页由 service 端处理；total 为该 user + type 过滤下所有 conversation 数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListResponse {

    private List<ConversationListItem> items;

    private long total;

    private int page;

    private int pageSize;
}
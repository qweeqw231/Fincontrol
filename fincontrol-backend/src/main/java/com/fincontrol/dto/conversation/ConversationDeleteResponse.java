package com.fincontrol.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1a.22 /api/conversations/{id} DELETE 响应（[api-contract.md §8.5](#)）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDeleteResponse {

    /** 物理删除的 chat_history 行数 */
    private int deletedMessageCount;
}
package com.fincontrol.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1a.18 意图分类结果（[api-contract.md §8.1](#)）。
 *
 * <p>仅在 {@code /api/chat/send} 响应中出现；用于前端展示 latency，便于评估 intent_classifier 性能。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassificationDto {

    /** true = 投资决策类（→ main_loop）；false = 其他（→ garbage_loop） */
    private boolean result;

    private int latencyMs;
}
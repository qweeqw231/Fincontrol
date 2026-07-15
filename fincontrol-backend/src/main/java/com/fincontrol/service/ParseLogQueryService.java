package com.fincontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.dto.screenshot.ParseLogItem;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析日志查询（Phase 1a.2 / [P0-1.4](#)）。
 *
 * <p>数据源：chat_history 表（conversation_type='screenshot_parse' AND role='assistant'），
 * 派生 logId + snapshotDate + fundCount + status 四个字段。
 *
 * <p>Phase 2 operation_log 上线后改为跨表查询（[decisions.md 决策 C 触发连锁](#)）。
 */
@Service
public class ParseLogQueryService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final ObjectMapper objectMapper;

    public ParseLogQueryService(ChatHistoryMapper chatHistoryMapper, ObjectMapper objectMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.objectMapper = objectMapper;
    }

    public List<ParseLogItem> listLatest(int limit) {
        List<ChatHistory> rows = chatHistoryMapper.selectAssistantByType(
                ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        List<ParseLogItem> items = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < limit; i++) {
            items.add(toItem(rows.get(i)));
        }
        return items;
    }

    private ParseLogItem toItem(ChatHistory msg) {
        ParseLogItem item = new ParseLogItem();
        item.setLogId(msg.getId());
        item.setConversationId(msg.getConversationId());
        // ChatHistory.createdAt 已是 LocalDateTime 类型，直接赋值
        item.setCreatedAt(msg.getCreatedAt());

        String content = msg.getContent();
        // 状态推断：content 含 "[error code=" 前缀 → parse_failed
        if (content != null && content.startsWith("[error code=")) {
            item.setStatus("parse_failed");
        } else {
            item.setStatus("imported");
        }

        // 尝试从 JSON 内容派生 snapshotDate 和 fundCount
        int fundCount = 0;
        LocalDate snapshotDate = null;
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.has("snapshot_date") && !root.path("snapshot_date").isNull()) {
                snapshotDate = LocalDate.parse(root.path("snapshot_date").asText());
            }
            if (root.has("categories") && root.path("categories").isArray()) {
                for (JsonNode c : root.path("categories")) {
                    if (c.has("funds") && c.path("funds").isArray()) {
                        fundCount += c.path("funds").size();
                    }
                }
            }
        } catch (Exception ignore) {
            // content 不一定是 JSON（可能含 Markdown 噪点），容忍失败
            // 0 基金场景下保留默认 0
        }
        item.setSnapshotDate(snapshotDate);
        item.setFundCount(fundCount);
        item.setSource("screenshot_manual");
        // confirmedAt 在 Phase 1 暂未对接（1a.3 之后实现），这里保持 null
        item.setConfirmedAt(null);
        return item;
    }
}

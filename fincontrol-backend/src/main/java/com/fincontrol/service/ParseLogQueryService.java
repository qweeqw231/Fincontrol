package com.fincontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.CategoryEnum;
import com.fincontrol.dto.screenshot.ParseLogItem;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析日志查询（Phase 1a.2 / [P0-1.4](#)）。
 *
 * <p>数据源：chat_history 表（conversation_type='screenshot_parse' AND role='assistant'），
 * 派生 logId + snapshotDate + fundCount + status + fundCountByCategory 字段。
 *
 * <p>1b.3 补救 R2：复用 {@link VisionModelClient#extractFirstJsonObject} 的相同候选扫描，
 * 支持纯 JSON / Markdown fenced JSON / 前后带说明文字。
 *
 * <p>Phase 2 operation_log 上线后改为跨表查询（[decisions.md 决策 C 触发连锁](#)）。
 */
@Service
public class ParseLogQueryService {

    private static final Logger log = LoggerFactory.getLogger(ParseLogQueryService.class);

    private final ChatHistoryMapper chatHistoryMapper;
    private final ObjectMapper objectMapper;
    private final VisionModelClient visionModelClient;

    public ParseLogQueryService(ChatHistoryMapper chatHistoryMapper,
                                ObjectMapper objectMapper,
                                VisionModelClient visionModelClient) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.objectMapper = objectMapper;
        this.visionModelClient = visionModelClient;
    }

    public List<ParseLogItem> listLatest(Long userId, int limit) {
        List<ChatHistory> rows = chatHistoryMapper.selectAssistantByType(
                userId, ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        List<ParseLogItem> items = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < limit; i++) {
            items.add(toItem(rows.get(i)));
        }
        return items;
    }

    public ParseLogItem toItem(ChatHistory msg) {
        ParseLogItem item = new ParseLogItem();
        item.setLogId(msg.getId());
        item.setConversationId(msg.getConversationId());
        item.setCreatedAt(msg.getCreatedAt());

        String content = msg.getContent();
        boolean isError = content != null && content.startsWith("[error code=");
        if (isError) {
            item.setStatus("parse_failed");
            item.setParseError(extractErrorMessage(content));
            item.setSource("screenshot_manual");
            return item;
        }
        item.setStatus("imported");
        item.setSource("screenshot_manual");

        // 1b.3 补救：复用视觉客户端的 JSON 候选扫描
        try {
            JsonNode root = visionModelClient.extractFirstJsonObject(content);
            populateFromJson(item, root);
        } catch (Exception ex) {
            // P5-2a 增强：模型响应无结构（典型如 0 基金或错误文本），不冒充 imported，标 parse_unknown
            item.setStatus("parse_unknown");
            item.setParseError("无法从模型响应中恢复结构：" + ex.getMessage());
        }
        return item;
    }

    private void populateFromJson(ParseLogItem item, JsonNode root) {
        if (root == null || !root.isObject()) {
            item.setParseError("模型响应不含可解析对象");
            return;
        }
        // 1) snapshot_date
        JsonNode dateNode = firstTextNode(root, "snapshot_date", "date");
        if (dateNode != null) {
            try {
                item.setSnapshotDate(LocalDate.parse(dateNode.asText()));
            } catch (Exception ignore) {
                // 留 null
            }
        }
        // 2) 各大类基金数
        int total = 0;
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        JsonNode categories = root.path("categories");
        if (categories.isArray()) {
            for (JsonNode c : categories) {
                String rawName = c.path("category_name").asText(null);
                String canonical = rawName == null ? null : CategoryEnum.fromAlias(rawName);
                if (canonical == null) continue;
                int n = 0;
                JsonNode funds = c.path("funds");
                if (funds.isArray()) n = funds.size();
                byCategory.merge(canonical, n, Integer::sum);
                total += n;
            }
        }
        // 兼容 holdings 旧 schema
        if (total == 0) {
            JsonNode holdings = root.path("holdings");
            if (holdings.isArray()) {
                total = holdings.size();
            }
        }
        item.setFundCount(total);
        if (!byCategory.isEmpty()) {
            item.setFundCountByCategory(byCategory);
        }
    }

    private static JsonNode firstTextNode(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.path(f);
            if (!v.isMissingNode() && !v.isNull()) return v;
        }
        return null;
    }

    private static String extractErrorMessage(String content) {
        if (content == null) return "解析失败";
        int close = content.indexOf(']');
        if (close < 0) return content.length() > 120 ? content.substring(0, 120) : content;
        int lineBreak = content.indexOf('\n', close);
        if (lineBreak < 0) return content.substring(0, Math.min(close + 1, content.length()));
        return content.substring(0, Math.min(lineBreak, content.length()));
    }
}

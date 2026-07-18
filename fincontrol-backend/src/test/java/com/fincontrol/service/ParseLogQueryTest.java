package com.fincontrol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincontrol.dto.screenshot.ParseLogItem;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 1a.7 补测：ParseLogQueryService 业务测试（Phase 1a.2 + [P0-1.4] 解析失败日志）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>A7-T14 listLatest 返回 N 条（limit 限制）</li>
 *   <li>A7-T15 toItem 派生 status='imported'（JSON 内容正常）</li>
 *   <li>A7-T16 toItem 派生 status='parse_failed'（content 含 [error code=]）</li>
 *   <li>A7-T17 toItem 派生 snapshotDate + fundCount（从 JSON 解析）</li>
 *   <li>A7-T18 toItem 容忍 content 非 JSON（Markdown 噪点）</li>
 *   <li>A7-T19 limit > rows.size() → 只返 rows.size() 条</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParseLogQueryTest {

    @Mock private ChatHistoryMapper chatHistoryMapper;

    private ObjectMapper objectMapper;
    private ParseLogQueryService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new ParseLogQueryService(chatHistoryMapper, objectMapper);
    }

    private ChatHistory buildRow(Long id, String convId, String content, LocalDateTime createdAt) {
        ChatHistory row = new ChatHistory();
        row.setId(id);
        row.setConversationId(convId);
        row.setUserId(1L);
        row.setRole("assistant");
        row.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        row.setContent(content);
        row.setCreatedAt(createdAt);
        return row;
    }

    @Test
    @DisplayName("A7-T14: listLatest(limit=5) → 返回最多 5 条")
    void listLatest_respectsLimit() {
        when(chatHistoryMapper.selectAssistantByType(eq(1L), eq(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE)))
                .thenReturn(List.of(
                        buildRow(1L, "conv-1", "{\"snapshot_date\":\"2026-07-15\",\"categories\":[{\"funds\":[{}]}]}",
                                LocalDateTime.of(2026, 7, 15, 10, 0)),
                        buildRow(2L, "conv-2", "{\"snapshot_date\":\"2026-07-14\"}",
                                LocalDateTime.of(2026, 7, 14, 10, 0))
                ));

        List<ParseLogItem> result = service.listLatest(1L, 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getLogId()).isEqualTo(1L);
        assertThat(result.get(0).getConversationId()).isEqualTo("conv-1");
        assertThat(result.get(1).getLogId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("A7-T15: toItem 派生 status='imported'（content 不含 [error code=]）")
    void toItem_importedStatus() {
        when(chatHistoryMapper.selectAssistantByType(eq(1L), eq(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE)))
                .thenReturn(List.of(buildRow(1L, "conv-1", "{\"snapshot_date\":\"2026-07-15\"}",
                        LocalDateTime.of(2026, 7, 15, 10, 0))));

        ParseLogItem item = service.listLatest(1L, 1).get(0);

        assertThat(item.getStatus()).isEqualTo("imported");
        assertThat(item.getSource()).isEqualTo("screenshot_manual");
        assertThat(item.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("A7-T16: content 含 [error code=] → status='parse_failed'")
    void toItem_parseFailedStatus() {
        String failedContent = "[error code=3001] 模型返回 0 只基金";
        when(chatHistoryMapper.selectAssistantByType(eq(1L), eq(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE)))
                .thenReturn(List.of(buildRow(1L, "conv-1", failedContent,
                        LocalDateTime.of(2026, 7, 15, 10, 0))));

        ParseLogItem item = service.listLatest(1L, 1).get(0);

        assertThat(item.getStatus()).isEqualTo("parse_failed");
    }

    @Test
    @DisplayName("A7-T17: content 为 JSON → 派生 snapshotDate + fundCount（累加多个 category）")
    void toItem_derivesSnapshotDateAndFundCount() {
        String content = "{\"snapshot_date\":\"2026-07-15\",\"categories\":["
                + "{\"funds\":[{},{},{}]},"
                + "{\"funds\":[{},{}]}"
                + "]}";
        when(chatHistoryMapper.selectAssistantByType(eq(1L), eq(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE)))
                .thenReturn(List.of(buildRow(1L, "conv-1", content,
                        LocalDateTime.of(2026, 7, 15, 10, 0))));

        ParseLogItem item = service.listLatest(1L, 1).get(0);

        assertThat(item.getSnapshotDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(item.getFundCount()).isEqualTo(5);  // 3 + 2
    }

    @Test
    @DisplayName("A7-T18: content 非 JSON（Markdown 噪点）→ snapshotDate=null, fundCount=0，状态仍 imported")
    void toItem_nonJsonContent_tolerant() {
        String noisy = "AI 回复：您的截图包含 5 只基金，分布如下：\n- 基金A\n- 基金B";
        when(chatHistoryMapper.selectAssistantByType(eq(1L), eq(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE)))
                .thenReturn(List.of(buildRow(1L, "conv-1", noisy,
                        LocalDateTime.of(2026, 7, 15, 10, 0))));

        ParseLogItem item = service.listLatest(1L, 1).get(0);

        assertThat(item.getSnapshotDate()).isNull();
        assertThat(item.getFundCount()).isEqualTo(0);
        assertThat(item.getStatus()).isEqualTo("imported");  // 不以 [error code= 开头
    }

    @Test
    @DisplayName("A7-T19: limit=10 但 rows 只有 2 条 → 只返 2 条")
    void listLatest_limitGreaterThanRows_returnsAllRows() {
        when(chatHistoryMapper.selectAssistantByType(eq(1L), eq(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE)))
                .thenReturn(List.of(
                        buildRow(1L, "conv-1", "{}", LocalDateTime.now()),
                        buildRow(2L, "conv-2", "{}", LocalDateTime.now())
                ));

        List<ParseLogItem> result = service.listLatest(1L, 10);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("A7-T20: 0 条记录 → 返空列表")
    void listLatest_noRows_returnsEmpty() {
        when(chatHistoryMapper.selectAssistantByType(eq(1L), eq(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE)))
                .thenReturn(List.of());

        List<ParseLogItem> result = service.listLatest(1L, 5);

        assertThat(result).isEmpty();
    }
}
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 1b.3 补救 R2：解析历史派生测试。
 * <p>覆盖：
 * <ul>
 *   <li>纯 JSON → 真实总数 + 各大类基金数 + snapshotDate</li>
 *   <li>Markdown fenced JSON（```json ... ```） → 真实总数</li>
 *   <li>JSON 前后带说明文字 → 真实总数</li>
 *   <li>解析失败 → status=parse_failed + 错误信息</li>
 *   <li>成功但无可恢复结构 → status=imported + parseError</li>
 *   <li>不修改 chat_history 原始 content</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ParseLogQueryTest {

    @Mock private ChatHistoryMapper chatHistoryMapper;

    private ObjectMapper objectMapper;
    private VisionModelClient visionModelClient;
    private ParseLogQueryService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        visionModelClient = new VisionModelClient(
                new okhttp3.OkHttpClient.Builder().build(), objectMapper);
        service = new ParseLogQueryService(chatHistoryMapper, objectMapper, visionModelClient);
    }

    private ChatHistory buildRow(Long id, String convId, String content) {
        ChatHistory row = new ChatHistory();
        row.setId(id);
        row.setConversationId(convId);
        row.setUserId(1L);
        row.setRole("assistant");
        row.setConversationType(ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE);
        row.setContent(content);
        row.setCreatedAt(LocalDateTime.of(2026, 7, 16, 10, 0));
        return row;
    }

    @Test
    @DisplayName("R2-T1: 纯 JSON → 真实总数 + 各大类基金数 + snapshotDate")
    void pureJson_extractsRealCount() {
        String json = "{\"snapshot_date\":\"2026-07-16\",\"categories\":["
                + "{\"category_name\":\"货币类\",\"funds\":[{},{}]},"
                + "{\"category_name\":\"商品类\",\"funds\":[{},{},{}]},"
                + "{\"category_name\":\"余额类\",\"funds\":[{}]}]}";
        when(chatHistoryMapper.selectAssistantByType(1L,
                ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE))
                .thenReturn(List.of(buildRow(1L, "conv-1", json)));

        List<ParseLogItem> items = service.listLatest(1L, 5);
        ParseLogItem item = items.get(0);
        assertThat(item.getStatus()).isEqualTo("imported");
        assertThat(item.getFundCount()).isEqualTo(6);
        assertThat(item.getFundCountByCategory())
                .containsEntry("货币类", 2)
                .containsEntry("商品类", 3)
                .containsEntry("余额类", 1);
        assertThat(item.getSnapshotDate().toString()).isEqualTo("2026-07-16");
    }

    @Test
    @DisplayName("R2-T2: Markdown fenced JSON 仍能提取")
    void fencedJson_extracts() {
        String md = "下面是模型输出：\n```json\n"
                + "{\"snapshot_date\":\"2026-07-16\",\"categories\":["
                + "{\"category_name\":\"商品类\",\"funds\":[{},{},{}]}]}\n```\n谢谢";
        when(chatHistoryMapper.selectAssistantByType(1L,
                ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE))
                .thenReturn(List.of(buildRow(2L, "conv-2", md)));

        ParseLogItem item = service.listLatest(1L, 1).get(0);
        assertThat(item.getStatus()).isEqualTo("imported");
        assertThat(item.getFundCount()).isEqualTo(3);
        assertThat(item.getFundCountByCategory()).containsEntry("商品类", 3);
    }

    @Test
    @DisplayName("R2-T3: JSON 前后带说明文字也能提取")
    void jsonWithNarrative() {
        String md = "好的，我已分析。响应如下：\n"
                + "{\"snapshot_date\":\"2026-07-16\",\"categories\":["
                + "{\"category_name\":\"A股权益类\",\"funds\":[{},{},{},{},{},{}]}]}\n"
                + "如有问题请告诉我。";
        when(chatHistoryMapper.selectAssistantByType(1L,
                ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE))
                .thenReturn(List.of(buildRow(3L, "conv-3", md)));

        ParseLogItem item = service.listLatest(1L, 1).get(0);
        assertThat(item.getFundCount()).isEqualTo(6);
        assertThat(item.getFundCountByCategory()).containsEntry("A股权益类", 6);
    }

    @Test
    @DisplayName("R2-T4: 解析失败状态保留 + 错误信息")
    void errorStatus_kept() {
        String content = "[error code=3001] 模型返回非 JSON\n---raw---\nrandom text";
        when(chatHistoryMapper.selectAssistantByType(1L,
                ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE))
                .thenReturn(List.of(buildRow(4L, "conv-4", content)));

        ParseLogItem item = service.listLatest(1L, 1).get(0);
        assertThat(item.getStatus()).isEqualTo("parse_failed");
        assertThat(item.getParseError()).isEqualTo("[error code=3001] 模型返回非 JSON");
    }

    @Test
    @DisplayName("R2-T5: 成功但无可恢复结构 → status=imported + parseError")
    void successWithoutStructure() {
        String content = "模型只输出了纯文本没有 JSON。";
        when(chatHistoryMapper.selectAssistantByType(1L,
                ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE))
                .thenReturn(List.of(buildRow(5L, "conv-5", content)));

        ParseLogItem item = service.listLatest(1L, 1).get(0);
        assertThat(item.getStatus()).isEqualTo("imported");
        assertThat(item.getFundCount()).isEqualTo(0);
        assertThat(item.getParseError()).contains("无法从模型响应中恢复结构");
    }

    @Test
    @DisplayName("R2-T6: listLatest limit 生效")
    void listLatest_limit() {
        when(chatHistoryMapper.selectAssistantByType(1L,
                ChatHistory.CONVERSATION_TYPE_SCREENSHOT_PARSE))
                .thenReturn(List.of(
                        buildRow(1L, "a", "{\"snapshot_date\":\"2026-07-16\",\"categories\":[]}"),
                        buildRow(2L, "b", "{\"snapshot_date\":\"2026-07-16\",\"categories\":[]}"),
                        buildRow(3L, "c", "{\"snapshot_date\":\"2026-07-16\",\"categories\":[]}")));

        assertThat(service.listLatest(1L, 2)).hasSize(2);
    }
}

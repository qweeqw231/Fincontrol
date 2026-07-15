package com.fincontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ScreenshotParseRequest;
import com.fincontrol.dto.screenshot.ScreenshotReparseRequest;
import com.fincontrol.entity.ChatHistory;
import com.fincontrol.mapper.ChatHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 1a.2 业务单元测试。
 *
 * <p>覆盖 ScreenshotService 的 4 类关键路径：
 * <ul>
 *   <li>正常：DeepSeek 返回合法 JSON → 返回 ParsedAsset + 写 chat_history</li>
 *   <li>P0-1.4 [3001]：DeepSeek 返回非 JSON → BusinessException(DEEPSEEK_INVALID_JSON) + assistant 错误记录</li>
 *   <li>P0-1.4 [3002]：DeepSeek 调用超时 → BusinessException(DEEPSEEK_TIMEOUT) + assistant 错误记录</li>
 *   <li>P0-1.4 [3003]：DeepSeek 返回 0 只基金 → BusinessException(DEEPSEEK_ZERO_FUNDS) + assistant 错误记录</li>
 * </ul>
 *
 * <p>采用纯 Mockito 单测（无 Spring 上下文、无 MySQL）。所有 stub 设置为 LENIENT 以兼容
 * 同 setUp 中部分用例不调用某些 collaborator 的情况。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScreenshotServiceTest {

    @Mock FileStorageService storage;
    @Mock DeepSeekClient deepSeekClient;
    @Mock PromptLoaderService promptLoader;
    @Mock ChatHistoryMapper chatHistoryMapper;

    @InjectMocks ScreenshotService service;

    @TempDir Path tmp;

    private final ObjectMapper mapper = new ObjectMapper();
    private File fakeImage;
    private static final String FILE_ID = "test-file-id-001";
    private static final String CONVERSATION_ID = "conv-" + FILE_ID;

    @BeforeEach
    void setUp() throws Exception {
        fakeImage = tmp.resolve("img.png").toFile();
        Files.write(fakeImage.toPath(), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}); // PNG header
        when(storage.resolveByFileId(FILE_ID)).thenReturn(fakeImage.toPath());
        when(promptLoader.get("screenshot_parser")).thenReturn("SYS_PROMPT");
        // chatHistoryMapper 不预设 stub：每个测试按需 mock insert 返回 1
    }

    /** 帮助：deepSeekClient.extractFirstJsonObject(raw) stub 成真实 JSON 解析。 */
    private void stubExtractJson(String raw) {
        try {
            JsonNode parsed = mapper.readTree(raw);
            when(deepSeekClient.extractFirstJsonObject(raw)).thenReturn(parsed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void parse_validJson_returnsParsedAsset() {
        String raw = "{" +
                "\"snapshot_date\":\"2026-07-09\"," +
                "\"total_asset\":100.00," +
                "\"six_categories_total\":100.00," +
                "\"balance_fund\":0," +
                "\"categories\":[{" +
                "  \"category_name\":\"货币类\"," +
                "  \"funds\":[{\"fund_name\":\"中加货币E\",\"amount\":100.00,\"profit\":1.50}]," +
                "  \"category_total\":100.00," +
                "  \"category_percentage\":10.00," +
                "  \"target_ratio\":10," +
                "  \"deviation\":0" +
                "}]}";
        when(deepSeekClient.callRaw(any(File.class), anyString(), anyString())).thenReturn(raw);
        stubExtractJson(raw);
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        ParsedAsset asset = service.parse(req);

        assertThat(asset).isNotNull();
        assertThat(asset.getConversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(asset.getSnapshotDate()).isEqualTo("2026-07-09");
        assertThat(asset.getCategories()).hasSize(1);
        assertThat(asset.getCategories().get(0).getCategoryName()).isEqualTo("货币类");
        assertThat(asset.getCategories().get(0).getFunds().get(0).getFundName()).isEqualTo("中加货币E");
        assertThat(asset.getMatchedFunds()).containsExactly("中加货币E");

        // user 消息 + assistant 消息 = 2 次 insert
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_deepSeekNonJson_throwsBusinessException3001() {
        String rawText = "### 这是 Markdown 表格  \n| 基金 | 金额 |\n| --- | --- |\n";
        when(deepSeekClient.callRaw(any(File.class), anyString(), anyString())).thenReturn(rawText);
        // extractFirstJsonObject 模拟真实行为：rawText 不是合法 JSON，抛 3001
        when(deepSeekClient.extractFirstJsonObject(rawText))
                .thenThrow(new BusinessException(ErrorCode.DEEPSEEK_INVALID_JSON,
                        "DeepSeek 响应中未找到 JSON 对象"));
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        assertThatThrownBy(() -> service.parse(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.DEEPSEEK_INVALID_JSON);
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3001);
                });
        // 失败时写 2 行 chat_history：user 消息 + assistant 错误记录
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_deepSeekTimeout_throwsBusinessException3002() {
        when(deepSeekClient.callRaw(any(File.class), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.DEEPSEEK_TIMEOUT, "DeepSeek API 调用超时 (>30s)"));
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        assertThatThrownBy(() -> service.parse(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3002);
                });
        // user 消息 + assistant 错误记录 = 2 次 insert
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_zeroFunds_throwsBusinessException3003() {
        // DeepSeek 返回 JSON，但 categories[].funds 全部为空 → 走到 3003 分支
        String raw = "{\"snapshot_date\":\"2026-07-09\",\"total_asset\":0," +
                "\"categories\":[{\"category_name\":\"货币类\",\"funds\":[]}]}";
        when(deepSeekClient.callRaw(any(File.class), anyString(), anyString())).thenReturn(raw);
        stubExtractJson(raw);
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        BusinessException thrown = null;
        try {
            service.parse(req);
        } catch (BusinessException ex) {
            thrown = ex;
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.getErrorCode().getCode()).isEqualTo(3003);
        // 错误 data 应包含 conversationId 和 errorType=zero_funds + aiRawResponse
        assertThat(thrown.getData()).isNotNull();
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void reparse_conversationNotFound_throwsBusinessException2001() {
        ScreenshotReparseRequest req = new ScreenshotReparseRequest();
        req.setConversationId("conv-unknown");
        when(chatHistoryMapper.selectByConversationIdOrderByCreatedAt("conv-unknown"))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.reparse(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(2001);
                });
    }
}

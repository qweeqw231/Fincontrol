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
 * Phase 1a.2 业务单元测试（Phase 1a.5 已切换视觉模型客户端）。
 *
 * <p>覆盖 ScreenshotService 的 4 类关键路径：
 * <ul>
 *   <li>正常：上游视觉模型返回合法 JSON → 返回 ParsedAsset + 写 chat_history</li>
 *   <li>[P0-1.4] [3001]：上游返回非 JSON → BusinessException(VISION_INVALID_JSON) + assistant 错误记录</li>
 *   <li>[P0-1.4] [3002]：上游调用超时 → BusinessException(VISION_TIMEOUT) + assistant 错误记录</li>
 *   <li>[P0-1.4] [3003]：上游返回 0 只基金 → BusinessException(VISION_ZERO_FUNDS) + assistant 错误记录</li>
 * </ul>
 *
 * <p>纯 Mockito 单测。所有 stub 设置为 LENIENT 以兼容同 setUp 中部分用例不调用某些 collaborator。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScreenshotServiceTest {

    @Mock FileStorageService storage;
    @Mock VisionModelClient visionModelClient;
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
        Files.write(fakeImage.toPath(), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        when(storage.resolveByFileId(FILE_ID)).thenReturn(fakeImage.toPath());
        when(promptLoader.get("screenshot_parser")).thenReturn("SYS_PROMPT");
    }

    private void stubExtractJson(String raw) {
        try {
            JsonNode parsed = mapper.readTree(raw);
            when(visionModelClient.extractFirstJsonObject(raw)).thenReturn(parsed);
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
        when(visionModelClient.callRaw(any(File.class), anyString(), anyString())).thenReturn(raw);
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

        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_visionNonJson_throwsBusinessException3001() {
        String rawText = "### 这是 Markdown 表格  \n| 基金 | 金额 |\n| --- | --- |\n";
        when(visionModelClient.callRaw(any(File.class), anyString(), anyString())).thenReturn(rawText);
        when(visionModelClient.extractFirstJsonObject(rawText))
                .thenThrow(new BusinessException(ErrorCode.VISION_INVALID_JSON,
                        "视觉模型响应中未找到 JSON 对象"));
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        assertThatThrownBy(() -> service.parse(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VISION_INVALID_JSON);
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3001);
                });
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_visionTimeout_throwsBusinessException3002() {
        when(visionModelClient.callRaw(any(File.class), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.VISION_TIMEOUT, "视觉模型调用超时 (>60s)"));
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
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_zeroFunds_throwsBusinessException3003() {
        String raw = "{\"snapshot_date\":\"2026-07-09\",\"total_asset\":0," +
                "\"categories\":[{\"category_name\":\"货币类\",\"funds\":[]}]}";
        when(visionModelClient.callRaw(any(File.class), anyString(), anyString())).thenReturn(raw);
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

    @Test
    void parse_minimaxJsonStyle_returnsParsedAsset() {
        // minimax M3 默认输出格式：顶层 holdings[] + 顶层 category_summary{}
        // 而不是 api-contract.md 设计的嵌套 categories[].funds[]
        String raw = "{" +
                "\"data_source\":\"alipay\"," +
                "\"date\":\"2026-07-15\"," +
                "\"holdings\":[" +
                "  {\"name\":\"天弘纳斯达克100指数(QDII)A\",\"amount\":633.32,\"category\":\"权益类\",\"holding_pnl\":51.32}," +
                "  {\"name\":\"国泰黄金ETF联接C\",\"amount\":564.86,\"category\":\"黄金类\",\"holding_pnl\":-45.25}," +
                "  {\"name\":\"余额宝\",\"amount\":320.85,\"category\":\"余额类\",\"holding_pnl\":1.89}" +
                "]," +
                "\"category_summary\":{" +
                "  \"权益类\":{\"total_amount\":633.32,\"total_ratio\":0.0803,\"total_holding_pnl\":51.32,\"count\":1}," +
                "  \"黄金类\":{\"total_amount\":564.86,\"total_ratio\":0.0716,\"total_holding_pnl\":-45.25,\"count\":1}," +
                "  \"余额类\":{\"total_amount\":320.85,\"total_ratio\":0.0407,\"total_holding_pnl\":1.89,\"count\":1}" +
                "}}";
        when(visionModelClient.callRaw(any(File.class), anyString(), anyString())).thenReturn(raw);
        stubExtractJson(raw);
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        ParsedAsset asset = service.parse(req);

        // Strategy B 验证：3 个类别 + 3 只基金
        assertThat(asset.getCategories()).hasSize(3);
        assertThat(asset.getMatchedFunds()).containsExactlyInAnyOrder(
                "天弘纳斯达克100指数(QDII)A", "国泰黄金ETF联接C", "余额宝");

        // 验证权益类的 category_total 从 category_summary 提取
        ParsedAsset.CategoryBlock equity = asset.getCategories().stream()
                .filter(b -> "权益类".equals(b.getCategoryName())).findFirst().orElseThrow();
        assertThat(equity.getFunds()).hasSize(1);
        assertThat(equity.getFunds().get(0).getFundName()).isEqualTo("天弘纳斯达克100指数(QDII)A");
        assertThat(equity.getFunds().get(0).getAmount()).isEqualTo(new java.math.BigDecimal("633.32"));
        assertThat(equity.getCategoryTotal()).isEqualTo(new java.math.BigDecimal("633.32"));
        assertThat(equity.getCategoryPercentage()).isEqualTo(new java.math.BigDecimal("0.0803"));
        assertThat(equity.getDeviation()).isEqualTo(new java.math.BigDecimal("51.32"));

        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }
}

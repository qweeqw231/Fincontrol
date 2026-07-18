package com.fincontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.ai.AiRouter;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 1a.2 业务单元测试（Phase 1a.5 视觉模型切换，1a.8 AI 韧性增强）。
 *
 * <p>1a.8：ScreenshotService 改用 {@link AiRouter} 统一入口，本 test mock {@code aiRouter.callVision}
 * 返回 {@link AiRouter.VisionResult}（含 {@code usedProvider} + {@code fallbackTriggered}）。
 *
 * <ul>
 *   <li>正常：上游视觉模型返回合法 JSON → 返回 ParsedAsset + 写 chat_history</li>
 *   <li>[P0-1.4] [3001]：上游返回非 JSON → BusinessException(VISION_INVALID_JSON) + assistant 错误记录</li>
 *   <li>[P0-1.4] [3002]：上游调用超时 → BusinessException(VISION_TIMEOUT) + assistant 错误记录</li>
 *   <li>[P0-1.4] [3003]：上游返回 0 只基金 → BusinessException(VISION_ZERO_FUNDS) + assistant 错误记录</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScreenshotServiceTest {

    @Mock FileStorageService storage;
    @Mock AiRouter aiRouter;
    @Mock VisionModelClient visionModelClient; // 仍需 mock 用于 extractFirstJsonObject
    @Mock PromptLoaderService promptLoader;
    @Mock ChatHistoryMapper chatHistoryMapper;

    private ScreenshotService service;

    @TempDir Path tmp;

    private final ObjectMapper mapper = new ObjectMapper();
    private Path ocrRoot;
    private File fakeImage;
    private static final String FILE_ID = "test-file-id-001";
    private static final String CONVERSATION_ID = "conv-" + FILE_ID;

    @BeforeEach
    void setUp() throws Exception {
        fakeImage = tmp.resolve("img.png").toFile();
        Files.write(fakeImage.toPath(), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        ocrRoot = tmp.resolve("ocr-results");
        service = new ScreenshotService(
                storage, visionModelClient, aiRouter, promptLoader, chatHistoryMapper, mapper, ocrRoot.toString());
        when(storage.resolveByFileId(FILE_ID)).thenReturn(fakeImage.toPath());
        when(promptLoader.get("screenshot_parser")).thenReturn("SYS_PROMPT");
    }

    private void stubVisionSuccess(String raw, String provider, boolean fallback) {
        when(aiRouter.callVision(any(File.class), anyString(), anyString(), anyInt()))
                .thenReturn(AiRouter.VisionResult.success(provider, fallback, raw));
    }

    private void stubExtractJson(String raw) {
        try {
            JsonNode parsed = mapper.readTree(raw);
            when(visionModelClient.extractFirstJsonObject(raw)).thenReturn(parsed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> ocrFiles() throws Exception {
        Path dateDir = ocrRoot.resolve(LocalDate.now().toString());
        if (!Files.isDirectory(dateDir)) return List.of();
        try (var stream = Files.list(dateDir)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    @Test
    void parse_validJson_returnsParsedAsset() throws Exception {
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
        stubVisionSuccess(raw, ChatHistory.PROVIDER_MINIMAX, false);
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

        List<Path> logs = ocrFiles();
        assertThat(logs).hasSize(1);
        JsonNode audit = mapper.readTree(logs.get(0).toFile());
        assertThat(audit.path("file_id").asText()).isEqualTo(FILE_ID);
        assertThat(audit.path("used_provider").asText()).isEqualTo(ChatHistory.PROVIDER_MINIMAX);
        assertThat(audit.path("fallback_triggered").asBoolean()).isFalse();
        assertThat(audit.path("raw_response").asText()).isEqualTo(raw);
        assertThat(audit.path("parsed").path("matched_funds").get(0).asText()).isEqualTo("中加货币E");

        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_successiveCalls_writeUniqueOcrFiles() throws Exception {
        String raw = "{\"snapshot_date\":\"2026-07-15\",\"total_asset\":100.00," +
                "\"categories\":[{\"category_name\":\"货币类\",\"funds\":[" +
                "{\"fund_name\":\"中加货币E\",\"amount\":100.00,\"profit\":1.50}]," +
                "\"category_total\":100.00}]}";
        stubVisionSuccess(raw, ChatHistory.PROVIDER_MINIMAX, false);
        stubExtractJson(raw);
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        service.parse(req);
        service.parse(req);

        List<Path> logs = ocrFiles();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getFileName().toString()).contains(FILE_ID).contains("minimax");
        assertThat(logs.get(1).getFileName().toString()).contains(FILE_ID).contains("minimax");
        assertThat(logs.get(0)).isNotEqualTo(logs.get(1));
        verify(chatHistoryMapper, times(4)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_visionNonJson_throwsBusinessException3001() throws Exception {
        String rawText = "### 这是 Markdown 表格  \n| 基金 | 金额 |\n| --- | --- |\n";
        stubVisionSuccess(rawText, ChatHistory.PROVIDER_MINIMAX, false);
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

        List<Path> logs = ocrFiles();
        assertThat(logs).hasSize(1);
        JsonNode audit = mapper.readTree(logs.get(0).toFile());
        assertThat(audit.path("file_id").asText()).isEqualTo(FILE_ID);
        assertThat(audit.path("used_provider").asText()).isEqualTo(ChatHistory.PROVIDER_MINIMAX);
        assertThat(audit.path("raw_response").asText()).isEqualTo(rawText);
        assertThat(audit.path("error_code").asInt()).isEqualTo(3001);
        assertThat(audit.path("error_message").asText()).contains("未找到 JSON");

        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    void parse_visionTimeout_throwsBusinessException3002() {
        // 1a.8：超时由 AiRouter 模拟抛 3002（AiRouter 内部不再 retry/fallback 时由 ScreenshotService 抛给用户）
        when(aiRouter.callVision(any(File.class), anyString(), anyString(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.VISION_TIMEOUT, "视觉模型调用超时 (>300s)"));
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
        stubVisionSuccess(raw, ChatHistory.PROVIDER_MINIMAX, false);
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
    void parse_v2PromptJsonStyle_returnsParsedAsset() {
        // 1a.8 v3: v2 prompt 输出 snake_case 嵌套结构（fund_name/category_name/profit），
        // 与历史 minimax 默认 holdings schema（name/category/holding_pnl）不同。
        String raw = "{" +
                "\"snapshot_date\":\"2026-07-15\"," +
                "\"total_asset\":7884.68," +
                "\"categories\":[" +
                "  {\"category_name\":\"货币类\",\"category_total\":796.32," +
                "   \"funds\":[{\"fund_name\":\"中加货币E\",\"amount\":796.32,\"profit\":2.32}]}," +
                "  {\"category_name\":\"商品类\",\"category_total\":1644.50," +
                "   \"funds\":[{\"fund_name\":\"国泰黄金ETF联接C\",\"amount\":564.86,\"profit\":-45.25}," +
                "           {\"fund_name\":\"国泰黄金ETF联接A\",\"amount\":923.16,\"profit\":-129.84}," +
                "           {\"fund_name\":\"华安黄金ETF联接C\",\"amount\":156.48,\"profit\":-26.27}]}" +
                "]}";
        stubVisionSuccess(raw, ChatHistory.PROVIDER_MINIMAX, false);
        stubExtractJson(raw);
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        ParsedAsset asset = service.parse(req);

        assertThat(asset.getTotalAsset()).isEqualByComparingTo(new java.math.BigDecimal("7884.68"));
        assertThat(asset.getCategories()).hasSize(2);
        ParsedAsset.CategoryBlock goods = asset.getCategories().stream()
                .filter(b -> "商品类".equals(b.getCategoryName())).findFirst().orElseThrow();
        assertThat(goods.getFunds()).hasSize(3);
        assertThat(goods.getFunds().get(0).getFundName()).isEqualTo("国泰黄金ETF联接C");
        assertThat(goods.getFunds().get(0).getAmount()).isEqualByComparingTo(new java.math.BigDecimal("564.86"));
        assertThat(goods.getFunds().get(0).getProfit()).isEqualByComparingTo(new java.math.BigDecimal("-45.25"));
    }

    @Test
    void parse_minimaxJsonStyle_returnsParsedAsset() {
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
        stubVisionSuccess(raw, ChatHistory.PROVIDER_MINIMAX, false);
        stubExtractJson(raw);
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ScreenshotParseRequest req = new ScreenshotParseRequest();
        req.setFileId(FILE_ID);
        req.setUserId(1L);

        ParsedAsset asset = service.parse(req);

        assertThat(asset.getCategories()).hasSize(3);
        assertThat(asset.getMatchedFunds()).containsExactlyInAnyOrder(
                "天弘纳斯达克100指数(QDII)A", "国泰黄金ETF联接C", "余额宝");

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

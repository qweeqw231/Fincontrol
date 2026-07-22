package com.fincontrol.service;

import com.fincontrol.dto.asset.AssetBalanceResponse;
import com.fincontrol.dto.asset.OperationsRecentResponse;
import com.fincontrol.dto.screenshot.ParseLogItem;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetRawQueryMapper;
import com.fincontrol.mapper.SnapshotMetaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 1a.4 + 1b.3 补救 AssetQueryService 业务测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SNAP_DATE = LocalDate.of(2026, 7, 16);

    @Mock private AssetRawMapper assetRawMapper;
    @Mock private AssetRawQueryMapper assetRawQueryMapper;
    @Mock private ParseLogQueryService parseLogQueryService;
    @Mock private CurrentSnapshotContext currentSnapshotContext;

    @InjectMocks private AssetQueryService service;

    private AssetRaw rawOf(String fund, BigDecimal amount, BigDecimal profit, boolean latest) {
        AssetRaw row = new AssetRaw();
        row.setUserId(USER_ID);
        row.setSnapshotDate(SNAP_DATE);
        row.setFundName(fund);
        row.setCategory("余额类");
        row.setAmount(amount);
        row.setProfit(profit);
        row.setSource("screenshot_manual");
        row.setIsLatest(latest);
        return row;
    }

    @BeforeEach
    void setUp() {
        when(currentSnapshotContext.resolveCurrentDate(USER_ID)).thenReturn(SNAP_DATE);
    }

    @Test
    @DisplayName("1b.3 余额汇总只读取 currentDate 的余额类 is_latest=1 行")
    void balance_currentDateOnly() {
        when(assetRawQueryMapper.selectCurrentBalanceByUser(USER_ID, SNAP_DATE))
                .thenReturn(List.of(
                        rawOf("余额宝", new BigDecimal("308.86"), new BigDecimal("1.90"), true)));

        AssetBalanceResponse resp = service.getBalance(USER_ID);

        assertThat(resp.getBalanceFundTotal()).isEqualByComparingTo("308.86");
        assertThat(resp.getSnapshotDate()).isEqualTo(SNAP_DATE);
    }

    @Test
    @DisplayName("1b.3 currentDate=null 时返回 total=0 + items=[]")
    void balance_noCurrentDate() {
        when(currentSnapshotContext.resolveCurrentDate(USER_ID)).thenReturn(null);
        AssetBalanceResponse resp = service.getBalance(USER_ID);
        assertThat(resp.getBalanceFundTotal()).isEqualByComparingTo("0");
        assertThat(resp.getItems()).isEmpty();
        assertThat(resp.getSnapshotDate()).isNull();
    }

    @Test
    @DisplayName("1b.3 R2 解析历史：fundCount>0 + status=imported → summary 显示真实只数")
    void operationsRecent_fundCount_positive() {
        ParseLogItem item = new ParseLogItem();
        item.setLogId(1L);
        item.setConversationId("conv-1");
        item.setSnapshotDate(SNAP_DATE);
        item.setFundCount(19);
        item.setStatus("imported");
        when(parseLogQueryService.listLatest(USER_ID, 5)).thenReturn(List.of(item));

        OperationsRecentResponse resp = service.getRecentOperations(USER_ID, 5);
        assertThat(resp.getItems().get(0).getSummary()).isEqualTo("解析 19 只基金");
    }

    @Test
    @DisplayName("1b.3 R2 解析历史：fundCount=0 + status=imported + parseError → 基金数未知 + 错误")
    void operationsRecent_fundCount_zero_with_error() {
        ParseLogItem item = new ParseLogItem();
        item.setLogId(2L);
        item.setConversationId("conv-2");
        item.setFundCount(0);
        item.setStatus("imported");
        item.setParseError("无法从模型响应中恢复结构：no JSON");
        when(parseLogQueryService.listLatest(USER_ID, 5)).thenReturn(List.of(item));

        OperationsRecentResponse resp = service.getRecentOperations(USER_ID, 5);
        assertThat(resp.getItems().get(0).getSummary())
                .isEqualTo("解析失败：无法从模型响应中恢复结构：no JSON");
    }

    @Test
    @DisplayName("1b.3 R2 解析历史：parse_failed → 截图解析失败")
    void operationsRecent_parseFailed() {
        ParseLogItem item = new ParseLogItem();
        item.setLogId(3L);
        item.setConversationId("conv-3");
        item.setFundCount(0);
        item.setStatus("parse_failed");
        when(parseLogQueryService.listLatest(USER_ID, 5)).thenReturn(List.of(item));

        OperationsRecentResponse resp = service.getRecentOperations(USER_ID, 5);
        assertThat(resp.getItems().get(0).getSummary()).isEqualTo("截图解析失败");
    }

    @Test
    @DisplayName("1b.3 R2 解析历史：fundCount=0 + 无 parseError + imported → 基金数未知")
    void operationsRecent_fundCount_zero_no_error() {
        ParseLogItem item = new ParseLogItem();
        item.setLogId(4L);
        item.setConversationId("conv-4");
        item.setFundCount(0);
        item.setStatus("imported");
        when(parseLogQueryService.listLatest(USER_ID, 5)).thenReturn(List.of(item));

        OperationsRecentResponse resp = service.getRecentOperations(USER_ID, 5);
        assertThat(resp.getItems().get(0).getSummary()).isEqualTo("基金数未知");
    }

    @Test
    @DisplayName("1b.3 累计收益 currentDate=null → available=false")
    void cumulativeReturn_noCurrentDate() {
        when(currentSnapshotContext.resolveCurrentDate(USER_ID)).thenReturn(null);
        var body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getAvailable()).isFalse();
        assertThat(body.getMessage()).isEqualTo("尚未上传资产快照");
    }

    @Test
    @DisplayName("1b.3 累计收益 currentDate 有值：按当日行汇总")
    void cumulativeReturn_currentDateAggregates() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", new BigDecimal("123.45"));
        row.put("total_holding_profit", new BigDecimal("20.00"));
        row.put("total_amount", new BigDecimal("7884.68"));
        row.put("fund_count", 19);
        when(assetRawQueryMapper.sumReturnFieldsAtDate(USER_ID, SNAP_DATE)).thenReturn(row);
        when(assetRawQueryMapper.selectCurrentBalanceByUser(USER_ID, SNAP_DATE))
                .thenReturn(Collections.emptyList());

        var body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getAvailable()).isTrue();
        assertThat(body.getFundCount()).isEqualTo(19);
        assertThat(body.getSnapshotDate()).isEqualTo("2026-07-16");
        assertThat(body.getTotalAmount()).isEqualByComparingTo("7884.68");
    }

    @Test
    @DisplayName("1b.3 决策25v3: 余额宝 holding=NULL+cumulative=1.90 → included, 校正 1.90")
    void cumulativeReturn_balanceFundFallback_included() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", new BigDecimal("-50.00"));
        row.put("total_holding_profit", new BigDecimal("-36.55"));
        row.put("total_amount", new BigDecimal("7884.68"));
        when(assetRawQueryMapper.sumReturnFieldsAtDate(USER_ID, SNAP_DATE)).thenReturn(row);
        com.fincontrol.entity.AssetRaw baoBao = new com.fincontrol.entity.AssetRaw();
        baoBao.setCategory("余额类");
        baoBao.setAmount(new BigDecimal("308.86"));
        baoBao.setHoldingProfit(null);
        baoBao.setCumulativeProfit(new BigDecimal("1.90"));
        when(assetRawQueryMapper.selectCurrentBalanceByUser(USER_ID, SNAP_DATE))
                .thenReturn(List.of(baoBao));

        var body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getBalanceFundStatus()).isEqualTo("included");
        assertThat(body.getBalanceFundAdjustment()).isEqualByComparingTo(new BigDecimal("1.90"));
        assertThat(body.getTotalHoldingProfit()).isEqualByComparingTo(new BigDecimal("-34.65"));
    }
}

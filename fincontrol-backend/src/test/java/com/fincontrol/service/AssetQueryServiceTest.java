package com.fincontrol.service;

import com.fincontrol.dto.asset.AssetBalanceResponse;
import com.fincontrol.dto.asset.OperationsRecentResponse;
import com.fincontrol.dto.screenshot.ParseLogItem;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.mapper.AssetRawMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.4 Slice C：AssetQueryService 业务测试（A4-S06 / S07 / S08）。
 */
@ExtendWith(MockitoExtension.class)
class AssetQueryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private AssetRawMapper assetRawMapper;

    @Mock
    private ParseLogQueryService parseLogQueryService;

    @InjectMocks
    private AssetQueryService service;

    private AssetRaw rawOf(String fund, BigDecimal amount, BigDecimal profit, boolean latest) {
        AssetRaw row = new AssetRaw();
        row.setUserId(USER_ID);
        row.setSnapshotDate(LocalDate.of(2026, 7, 16));
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
    }

    @Test
    @DisplayName("A4-S06: 余额汇总只累加 余额类+is_latest=true")
    void balance_onlyBalanceCategoryAndLatest() {
        when(assetRawMapper.selectBalanceByUser(USER_ID)).thenReturn(List.of(
                rawOf("余额宝", new BigDecimal("418.46"), new BigDecimal("1.56"), true),
                rawOf("余额", new BigDecimal("0.00"), BigDecimal.ZERO, true),
                rawOf("IGNORE", new BigDecimal("999.00"), BigDecimal.ZERO, false)));

        AssetBalanceResponse resp = service.getBalance(USER_ID);

        assertThat(resp.getBalanceFundTotal()).isEqualByComparingTo("418.46");
        assertThat(resp.getItems()).hasSize(2);
        assertThat(resp.getItems().get(0).getFundName()).isEqualTo("余额宝");
        assertThat(resp.getItems().get(0).getCategory()).isEqualTo("余额类");
        assertThat(resp.getItems().get(0).getProfit()).isEqualByComparingTo("1.56");
        assertThat(resp.getSnapshotDate()).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    @Test
    @DisplayName("A4-S06: 余额无数据返回 total=0 + items=[]")
    void balance_emptyData() {
        when(assetRawMapper.selectBalanceByUser(USER_ID)).thenReturn(Collections.emptyList());

        AssetBalanceResponse resp = service.getBalance(USER_ID);

        assertThat(resp.getBalanceFundTotal()).isEqualByComparingTo("0");
        assertThat(resp.getItems()).isEmpty();
        assertThat(resp.getSnapshotDate()).isNull();
    }

    @Test
    @DisplayName("A4-S07: 解析活动使用 chat_history 派生，不宣称为入库")
    void operationsRecent_derivedFromParseLogs() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 10, 30);
        ParseLogItem success = new ParseLogItem();
        success.setLogId(1L);
        success.setConversationId("conv-1");
        success.setSnapshotDate(LocalDate.of(2026, 7, 16));
        success.setFundCount(18);
        success.setStatus("imported");
        success.setCreatedAt(now);
        ParseLogItem failed = new ParseLogItem();
        failed.setLogId(2L);
        failed.setConversationId("conv-2");
        failed.setFundCount(0);
        failed.setStatus("parse_failed");
        failed.setCreatedAt(now.minusMinutes(5));
        when(parseLogQueryService.listLatest(USER_ID, 5)).thenReturn(List.of(success, failed));

        OperationsRecentResponse resp = service.getRecentOperations(USER_ID, 5);

        assertThat(resp.getItems()).hasSize(2);
        assertThat(resp.getItems().get(0).getOperationType()).isEqualTo("screenshot_parse");
        assertThat(resp.getItems().get(0).getSummary()).isEqualTo("解析 18 只基金");
        assertThat(resp.getItems().get(1).getSummary()).isEqualTo("截图解析失败");
        verify(parseLogQueryService).listLatest(USER_ID, 5);
    }

    @Test
    @DisplayName("A4-S07: 解析活动 limit 自动夹在 1–100")
    void operationsRecent_limitClamp() {
        when(parseLogQueryService.listLatest(USER_ID, 1)).thenReturn(Collections.emptyList());
        when(parseLogQueryService.listLatest(USER_ID, 100)).thenReturn(Collections.emptyList());

        service.getRecentOperations(USER_ID, 0);
        service.getRecentOperations(USER_ID, 9999);

        verify(parseLogQueryService).listLatest(USER_ID, 1);
        verify(parseLogQueryService).listLatest(USER_ID, 100);
    }

    @Test
    @DisplayName("1b.2 累计 + 持有 收益: 正常（正累计 + 正持有，返 5 字段）")
    void cumulativeReturn_positiveCumAndHold() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", new BigDecimal("123.45"));
        row.put("total_holding_profit",    new BigDecimal("20.00"));
        row.put("total_amount",             new BigDecimal("1000.00"));
        row.put("fund_count",               19);
        row.put("snapshot_date",            "2026-07-16");
        when(assetRawMapper.sumReturnFieldsByUser(USER_ID)).thenReturn(row);

        com.fincontrol.dto.asset.CumulativeReturnResponse body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getAvailable()).isTrue();
        assertThat(body.getAlgorithm()).isEqualTo("phase1_simple");
        assertThat(body.getTotalCumulativeProfit()).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(body.getTotalHoldingProfit()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(body.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(body.getReturnRate()).isEqualByComparingTo(new BigDecimal("0.123450"));
        assertThat(body.getHoldingReturnRate()).isEqualByComparingTo(new BigDecimal("0.020000"));
        assertThat(body.getFundCount()).isEqualTo(19);
        assertThat(body.getSnapshotDate()).isEqualTo("2026-07-16");
    }

    @Test
    @DisplayName("1b.2 累计 + 持有 收益: 负累计 / 正持有（典型持仓场景：累计亏损但浮盈）")
    void cumulativeReturn_negativeCumPositiveHold() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", new BigDecimal("-50.00"));
        row.put("total_holding_profit",    new BigDecimal("30.00"));
        row.put("total_amount",             new BigDecimal("1000.00"));
        when(assetRawMapper.sumReturnFieldsByUser(USER_ID)).thenReturn(row);

        com.fincontrol.dto.asset.CumulativeReturnResponse body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getReturnRate()).isEqualByComparingTo(new BigDecimal("-0.050000"));
        assertThat(body.getHoldingReturnRate()).isEqualByComparingTo(new BigDecimal("0.030000"));
    }

    @Test
    @DisplayName("1b.2 累计 + 持有 收益: 总额为 0 时两个 returnRate=0（避免除零）")
    void cumulativeReturn_zeroAmount_returnsZero() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", BigDecimal.ZERO);
        row.put("total_holding_profit",    BigDecimal.ZERO);
        row.put("total_amount",             BigDecimal.ZERO);
        when(assetRawMapper.sumReturnFieldsByUser(USER_ID)).thenReturn(row);

        com.fincontrol.dto.asset.CumulativeReturnResponse body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getHoldingReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getAvailable()).isTrue();
    }

    // ============================================================
    // 决策 25 v3：余额宝 fallback 校正测试
    // ============================================================

    @Test
    @DisplayName("1b.2 决策25v3: 余额宝 holding=NULL+cumulative=1.90 → fallback → status=included, totalHolding = raw + 1.90")
    void cumulativeReturn_balanceFundFallback_included() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", new BigDecimal("-50.00"));
        row.put("total_holding_profit",    new BigDecimal("-36.55"));  // 不含余额宝（已 SUM 但余额宝 holding=NULL 不计入）
        row.put("total_amount",             new BigDecimal("1000.00"));
        row.put("fund_count",               19);
        row.put("snapshot_date",            "2026-07-16");
        when(assetRawMapper.sumReturnFieldsByUser(USER_ID)).thenReturn(row);

        // 模拟 selectBalanceByUser 返余额宝行（holding=NULL, cumulative=1.90）
        com.fincontrol.entity.AssetRaw baoBao = new com.fincontrol.entity.AssetRaw();
        baoBao.setCategory("余额类");
        baoBao.setAmount(new BigDecimal("308.86"));
        baoBao.setHoldingProfit(null);  // 关键：NULL
        baoBao.setCumulativeProfit(new BigDecimal("1.90"));
        when(assetRawMapper.selectBalanceByUser(USER_ID)).thenReturn(List.of(baoBao));

        com.fincontrol.dto.asset.CumulativeReturnResponse body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getBalanceFundStatus()).isEqualTo("included");
        assertThat(body.getBalanceFundAdjustment()).isEqualByComparingTo(new BigDecimal("1.90"));
        assertThat(body.getRawHoldingProfit()).isEqualByComparingTo(new BigDecimal("-36.55"));
        // 校正后: -36.55 + 1.90 = -34.65
        assertThat(body.getTotalHoldingProfit()).isEqualByComparingTo(new BigDecimal("-34.65"));
        // holdingReturnRate 重新算: -34.65 / 1000 = -0.034650
        assertThat(body.getHoldingReturnRate()).isEqualByComparingTo(new BigDecimal("-0.034650"));
    }

    @Test
    @DisplayName("1b.2 决策25v3: 余额宝两项都 NULL → status=excluded_unknown, adjustment=0")
    void cumulativeReturn_balanceFundFallback_excludedUnknown() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", new BigDecimal("-50.00"));
        row.put("total_holding_profit",    new BigDecimal("-36.55"));
        row.put("total_amount",             new BigDecimal("1000.00"));
        when(assetRawMapper.sumReturnFieldsByUser(USER_ID)).thenReturn(row);

        // 模拟 selectBalanceByUser 返余额宝行（两项都 NULL）
        com.fincontrol.entity.AssetRaw baoBao = new com.fincontrol.entity.AssetRaw();
        baoBao.setCategory("余额类");
        baoBao.setAmount(new BigDecimal("308.86"));
        baoBao.setHoldingProfit(null);
        baoBao.setCumulativeProfit(null);  // 也 NULL
        when(assetRawMapper.selectBalanceByUser(USER_ID)).thenReturn(List.of(baoBao));

        com.fincontrol.dto.asset.CumulativeReturnResponse body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getBalanceFundStatus()).isEqualTo("excluded_unknown");
        assertThat(body.getBalanceFundAdjustment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getTotalHoldingProfit()).isEqualByComparingTo(new BigDecimal("-36.55"));
    }

    @Test
    @DisplayName("1b.2 决策25v3: 没余额类持仓 → status=normal, 不校正")
    void cumulativeReturn_balanceFundFallback_normal() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("total_cumulative_profit", new BigDecimal("-50.00"));
        row.put("total_holding_profit",    new BigDecimal("20.00"));
        row.put("total_amount",             new BigDecimal("1000.00"));
        when(assetRawMapper.sumReturnFieldsByUser(USER_ID)).thenReturn(row);

        // 模拟 selectBalanceByUser 返空（无余额类持仓）
        when(assetRawMapper.selectBalanceByUser(USER_ID)).thenReturn(Collections.emptyList());

        com.fincontrol.dto.asset.CumulativeReturnResponse body = service.getCumulativeReturn(USER_ID);
        assertThat(body.getBalanceFundStatus()).isEqualTo("normal");
        assertThat(body.getBalanceFundAdjustment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.getTotalHoldingProfit()).isEqualByComparingTo(new BigDecimal("20.00"));  // 不校正
    }
}



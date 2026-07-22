package com.fincontrol.controller;

import com.fincontrol.dto.asset.AssetBalanceItem;
import com.fincontrol.dto.asset.AssetBalanceResponse;
import com.fincontrol.dto.asset.OperationRecentItem;
import com.fincontrol.dto.asset.OperationsRecentResponse;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.ChatHistoryMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.AssetQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1a.4 Slice C：{@link AssetController} 契约测试。
 *
 * <p>只 mock {@link AssetQueryService}，不加载 Mapper / DB；覆盖 balance /
 * operations/recent / cumulative-return 三个端点的 URL、响应包装、
 * userId 隔离、limit 默认值和 phase 1 占位。
 */
@WebMvcTest(
        value = AssetController.class,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
@TestPropertySource(properties = "spring.profiles.active=local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssetQueryService assetQueryService;
    // 不在这里用 @MockBean 占位；测试 body=null 时，MockMvc 会调用真实 Bean。
    @MockBean
    private com.fincontrol.mapper.AssetRawQueryMapper assetRawQueryMapper;
    @MockBean
    private com.fincontrol.mapper.AssetRawMapper assetRawMapper;
    @MockBean
    private com.fincontrol.mapper.UserConfigMapper userConfigMapper;
    @MockBean
    private com.fincontrol.service.CurrentSnapshotContext currentSnapshotContext;
    @MockBean
    private com.fincontrol.service.ParseLogQueryService parseLogQueryService;
    @MockBean
    private com.fincontrol.service.VisionModelClient visionModelClient;

    @MockBean
    private AssetSnapshotMapper assetSnapshotMapper;

    @MockBean
    private ChatHistoryMapper chatHistoryMapper;

    @MockBean
    private FundCategoryMapMapper fundCategoryMapMapper;
    @MockBean
    private com.fincontrol.mapper.CategoryMasterMapper categoryMasterMapper;

    @Test
    void balance_defaultUserId_returnsData() throws Exception {
        AssetBalanceResponse resp = AssetBalanceResponse.builder()
                .balanceFundTotal(new BigDecimal("418.46"))
                .items(List.of(AssetBalanceItem.builder()
                        .fundName("余额宝")
                        .amount(new BigDecimal("418.46"))
                        .profit(new BigDecimal("1.56"))
                        .category("余额类")
                        .build()))
                .snapshotDate(LocalDate.of(2026, 7, 16))
                .build();
        when(assetQueryService.getBalance(anyLong())).thenReturn(resp);

        mockMvc.perform(get("/api/asset/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.balanceFundTotal").value(418.46))
                .andExpect(jsonPath("$.data.items[0].fundName").value("余额宝"))
                .andExpect(jsonPath("$.data.items[0].category").value("余额类"));
        verify(assetQueryService).getBalance(1L);
    }

    @Test
    void balance_explicitUserId_passedToService() throws Exception {
        when(assetQueryService.getBalance(anyLong()))
                .thenReturn(AssetBalanceResponse.builder().balanceFundTotal(BigDecimal.ZERO).items(List.of()).build());

        mockMvc.perform(get("/api/asset/balance").header("X-User-Id", "7"))
                .andExpect(status().isOk());
        verify(assetQueryService).getBalance(7L);
    }

    @Test
    void balance_emptyData_returnsEmptyItemsAndZero() throws Exception {
        when(assetQueryService.getBalance(anyLong()))
                .thenReturn(AssetBalanceResponse.builder().balanceFundTotal(BigDecimal.ZERO).items(List.of()).build());

        mockMvc.perform(get("/api/asset/balance").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.balanceFundTotal").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void operationsRecent_defaultLimitFive_returnsData() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 10, 30);
        OperationsRecentResponse resp = OperationsRecentResponse.builder()
                .items(List.of(OperationRecentItem.builder()
                        .operationType("screenshot_parse")
                        .operationDate(now)
                        .summary("解析 18 只基金")
                        .snapshotDate(LocalDate.of(2026, 7, 16))
                        .build()))
                .build();
        when(assetQueryService.getRecentOperations(anyLong(), anyInt())).thenReturn(resp);

        mockMvc.perform(get("/api/asset/operations/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].operationType").value("screenshot_parse"))
                .andExpect(jsonPath("$.data.items[0].summary").value("解析 18 只基金"));
        verify(assetQueryService).getRecentOperations(1L, 5);
    }

    @Test
    void operationsRecent_explicitLimit_passesThrough() throws Exception {
        when(assetQueryService.getRecentOperations(anyLong(), anyInt()))
                .thenReturn(OperationsRecentResponse.builder().items(List.of()).build());

        mockMvc.perform(get("/api/asset/operations/recent?limit=12").header("X-User-Id", "5"))
                .andExpect(status().isOk());
        verify(assetQueryService).getRecentOperations(5L, 12);
    }

    @Test
    void operationsRecent_failedSummary_stillSaysParse() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 9, 0);
        OperationsRecentResponse resp = OperationsRecentResponse.builder()
                .items(List.of(OperationRecentItem.builder()
                        .operationType("screenshot_parse")
                        .operationDate(now)
                        .summary("截图解析失败")
                        .build()))
                .build();
        when(assetQueryService.getRecentOperations(anyLong(), anyInt())).thenReturn(resp);

        mockMvc.perform(get("/api/asset/operations/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].summary").value("截图解析失败"));
    }

    @Test
    void cumulativeReturn_phase1Simple_returnsAlgorithmFields() throws Exception {
        // 1b.2 决策 4 v2：累计收益率算法 = Σcumulative/Σamount，algorithm=phase1_simple
        com.fincontrol.dto.asset.CumulativeReturnResponse body = com.fincontrol.dto.asset.CumulativeReturnResponse.builder()
                .available(true)
                .algorithm("phase1_simple")
                .totalCumulativeProfit(new java.math.BigDecimal("100.00"))
                .totalAmount(new java.math.BigDecimal("2000.00"))
                .returnRate(new java.math.BigDecimal("0.050000"))
                .message(null)
                .build();
        when(assetQueryService.getCumulativeReturn(anyLong())).thenReturn(body);

        mockMvc.perform(get("/api/asset/cumulative-return").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.algorithm").value("phase1_simple"))
                .andExpect(jsonPath("$.data.returnRate").value(0.05));
    }
}



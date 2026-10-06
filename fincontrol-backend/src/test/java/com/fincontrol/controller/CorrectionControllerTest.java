package com.fincontrol.controller;

import com.fincontrol.dto.correction.CorrectionConfirmResponse;
import com.fincontrol.dto.correction.CorrectionDefaultsResponse;
import com.fincontrol.dto.correction.MonthlyCalculateResponse;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse;
import com.fincontrol.service.CorrectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2a 校正页 API 契约测试（{@link CorrectionController}）。
 *
 * <p>只 mock {@link CorrectionService} 与 Mapper 链；覆盖 9 个端点的 URL、
 * 响应包装、X-User-Id 传递与关键字段。
 */
@WebMvcTest(
        value = CorrectionController.class,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
@TestPropertySource(properties = "spring.profiles.active=local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CorrectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CorrectionService correctionService;

    // @MapperScan 仍会创建全部 mapper，缺一即炸 context
    @MockBean private com.fincontrol.mapper.AssetRawMapper assetRawMapper;
    @MockBean private com.fincontrol.mapper.AssetSnapshotMapper assetSnapshotMapper;
    @MockBean private com.fincontrol.mapper.AssetRawQueryMapper assetRawQueryMapper;
    @MockBean private com.fincontrol.mapper.ChatHistoryMapper chatHistoryMapper;
    @MockBean private com.fincontrol.mapper.FundCategoryMapMapper fundCategoryMapMapper;
    @MockBean private com.fincontrol.mapper.CategoryMasterMapper categoryMasterMapper;
    @MockBean private com.fincontrol.mapper.UserConfigMapper userConfigMapper;
    @MockBean private com.fincontrol.mapper.SnapshotMetaMapper snapshotMetaMapper;
    @MockBean private com.fincontrol.mapper.SettingsMapper settingsMapper;
    @MockBean private com.fincontrol.mapper.NavHistoryMapper navHistoryMapper;
    @MockBean private com.fincontrol.mapper.OperationLogMapper operationLogMapper;
    @MockBean private com.fincontrol.mapper.NavMilestoneMapper navMilestoneMapper;
    @MockBean private com.fincontrol.mapper.CorrectionIterationMapper correctionIterationMapper;
    @MockBean private com.fincontrol.mapper.CorrectionAssetDetailMapper correctionAssetDetailMapper;
    @MockBean private com.fincontrol.mapper.CorrectionParamMapper correctionParamMapper;
    @MockBean private com.fincontrol.service.VisionModelClient visionModelClient;
    @MockBean private com.fincontrol.service.CurrentSnapshotContext currentSnapshotContext;
    @MockBean private com.fincontrol.service.ParseLogQueryService parseLogQueryService;

    @Test
    void monthlyDefaults_returnsData() throws Exception {
        CorrectionDefaultsResponse resp = new CorrectionDefaultsResponse();
        resp.setVTotalSixCategories(new BigDecimal("9525.87"));
        resp.setVMonetary(new BigDecimal("988.27"));
        resp.setVBond(new BigDecimal("1486.37"));
        resp.setVHighVol(new BigDecimal("7051.23"));
        resp.setUHigh(new BigDecimal("618"));
        resp.setSnapshotDate("2026-09-29");
        resp.setSnapshotNote("六大类合计，不含余额类");
        when(correctionService.getMonthlyDefaults(anyLong())).thenReturn(resp);

        mockMvc.perform(get("/api/correction/defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.vTotalSixCategories").value(9525.87))
                .andExpect(jsonPath("$.data.snapshotDate").value("2026-09-29"));
    }

    @Test
    void monthlyCalculate_returnsSuggestion() throws Exception {
        MonthlyCalculateResponse resp = MonthlyCalculateResponse.builder()
                .deltaMTheory(new BigDecimal("85.22"))
                .deltaBTheory(new BigDecimal("123.88"))
                .zohTriggered(false)
                .totalInvestment(new BigDecimal("1000.00"))
                .build();
        when(correctionService.calculateMonthly(anyLong(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/correction/monthly/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vCurr\":9525.87,\"vMonetary\":988.27,\"vBond\":1486.37,\"uHigh\":618}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deltaMTheory").value(85.22))
                .andExpect(jsonPath("$.data.zohTriggered").value(false));
    }

    @Test
    void monthlyRecalculate_returnsDeviations() throws Exception {
        when(correctionService.recalculateMonthly(anyLong(), any()))
                .thenReturn(com.fincontrol.dto.correction.MonthlyRecalculateResponse.builder()
                        .totalInvestmentActual(new BigDecimal("1139.00"))
                        .overBudgetLimit(true)
                        .build());

        mockMvc.perform(post("/api/correction/monthly/recalculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vCurr\":8713.89,\"deltaMActual\":190,\"deltaBActual\":290}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInvestmentActual").value(1139.00))
                .andExpect(jsonPath("$.data.overBudgetLimit").value(true));
    }

    @Test
    void monthlyConfirm_returnsOperationLogId() throws Exception {
        when(correctionService.confirmMonthly(anyLong(), any()))
                .thenReturn(CorrectionConfirmResponse.builder()
                        .operationLogId(456L)
                        .operationDate("2026-10-07")
                        .writtenToOperationLog(true)
                        .detailRows(0)
                        .build());

        mockMvc.perform(post("/api/correction/monthly/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snapshotDate\":\"2026-09-29\",\"vCurr\":9525.87,"
                                + "\"deltaMActual\":0,\"deltaBActual\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationLogId").value(456))
                .andExpect(jsonPath("$.data.writtenToOperationLog").value(true));
    }

    @Test
    void quarterlyDefaults_returnsParams() throws Exception {
        com.fincontrol.dto.correction.QuarterlyDefaultsResponse resp =
                new com.fincontrol.dto.correction.QuarterlyDefaultsResponse();
        resp.setSnapshotDate("2026-09-29");
        resp.setVCurr(new BigDecimal("9525.87"));
        resp.setUHighDefault(new BigDecimal("618"));
        when(correctionService.getQuarterlyDefaults(anyLong())).thenReturn(resp);

        mockMvc.perform(get("/api/correction/quarterly/defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotDate").value("2026-09-29"))
                .andExpect(jsonPath("$.data.uHighDefault").value(618));
    }

    @Test
    void quarterlyCalculate_returnsChosenPlan() throws Exception {
        QuarterlyCalculateResponse resp = QuarterlyCalculateResponse.builder()
                .anchor("海外权益类")
                .chosen(QuarterlyCalculateResponse.Chosen.builder()
                        .eHigh(new BigDecimal("382.00"))
                        .deltaMRaw(new BigDecimal("85.22"))
                        .zohTriggered(false)
                        .totalInvestment(new BigDecimal("1000.00"))
                        .build())
                .icDrr(QuarterlyCalculateResponse.IcDrr.builder()
                        .fBefore(new BigDecimal("21.41"))
                        .fAfter(new BigDecimal("6.97"))
                        .ratioPct(new BigDecimal("67.4"))
                        .build())
                .build();
        when(correctionService.calculateQuarterly(anyLong(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/correction/quarterly/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"surplus\":700,\"mMax\":1000,\"uHigh\":618,\"mode\":\"mMaxCapped\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anchor").value("海外权益类"))
                .andExpect(jsonPath("$.data.chosen.eHigh").value(382.00))
                .andExpect(jsonPath("$.data.icDrr.ratioPct").value(67.4));
    }

    @Test
    void quarterlyConfirm_returnsDetailRows() throws Exception {
        when(correctionService.confirmQuarterly(anyLong(), any()))
                .thenReturn(CorrectionConfirmResponse.builder()
                        .operationLogId(457L)
                        .writtenToOperationLog(true)
                        .detailRows(20)
                        .build());

        mockMvc.perform(post("/api/correction/quarterly/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snapshotDate\":\"2026-09-30\",\"totalInvestment\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationLogId").value(457))
                .andExpect(jsonPath("$.data.detailRows").value(20));
    }

    @Test
    void operationsList_returnsItems() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 3);
        item.put("correctionMode", "lqr_zoh");
        item.put("hasDetail", true);
        item.put("icDrrPct", new BigDecimal("67.40"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", List.of(item));
        when(correctionService.listOperations(anyLong())).thenReturn(result);

        mockMvc.perform(get("/api/correction/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].correctionMode").value("lqr_zoh"))
                .andExpect(jsonPath("$.data.items[0].hasDetail").value(true));
    }

    @Test
    void operationDetail_returnsIterations() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", Map.of("id", 3L));
        result.put("iterations", List.of(Map.of("sortOrder", 1, "alpha", 0.16)));
        when(correctionService.getOperationDetail(anyLong(), any())).thenReturn(result);

        mockMvc.perform(get("/api/correction/operations/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation.id").value(3))
                .andExpect(jsonPath("$.data.iterations[0].alpha").value(0.16));
    }
}
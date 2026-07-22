package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.snapshot.SnapshotByDateResponse;
import com.fincontrol.dto.snapshot.SnapshotHistoryItem;
import com.fincontrol.dto.snapshot.SnapshotHistoryResponse;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.ChatHistoryMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.SnapShotConfirmService;
import com.fincontrol.service.SnapshotQueryService;
import com.fincontrol.service.SnapshotMetaService;
import com.fincontrol.service.SnapshotRollbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1a.4 Slice A/B：{@link SnapshotController} 契约测试。
 *
 * <p>只 mock {@link SnapshotQueryService}，不加载 Mapper / DB；覆盖 7 个端点的 URL、
 * 响应包装、userId 隔离、page/pageSize 边界和异常码透传。
 */
@WebMvcTest(
        value = SnapshotController.class,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
@TestPropertySource(properties = "spring.profiles.active=local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SnapshotQueryService snapshotQueryService;

    @MockBean
    private SnapShotConfirmService snapShotConfirmService;

    @MockBean
    private SnapshotRollbackService snapshotRollbackService;
    @MockBean
    private SnapshotMetaService snapshotMetaService; // 1b.3.3

    @MockBean
    private AssetRawMapper assetRawMapper;

    @MockBean
    private AssetSnapshotMapper assetSnapshotMapper;

    @MockBean
    private ChatHistoryMapper chatHistoryMapper;

    @MockBean
    private FundCategoryMapMapper fundCategoryMapMapper;
    @MockBean
    private com.fincontrol.mapper.CategoryMasterMapper categoryMasterMapper;

    private SnapshotLatestResponse latestFixture() {
        return SnapshotLatestResponse.builder()
                .snapshotDate(LocalDate.of(2026, 7, 16))
                .sixCategoriesTotal(new BigDecimal("1595.73"))
                .balanceFund(new BigDecimal("418.46"))
                .totalAssetWithBalance(new BigDecimal("2014.19"))
                .categories(List.of())
                .build();
    }

    @Test
    void latest_defaultRequest_returnsData() throws Exception {
        when(snapshotQueryService.getLatest(anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(latestFixture());

        mockMvc.perform(get("/api/snapshot/latest").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.snapshotDate").value("2026-07-16"))
                .andExpect(jsonPath("$.data.sixCategoriesTotal").value(1595.73))
                .andExpect(jsonPath("$.data.balanceFund").value(418.46))
                .andExpect(jsonPath("$.data.totalAssetWithBalance").value(2014.19));

        verify(snapshotQueryService).getLatest(1L, false, true);
    }

    @Test
    void latest_defaultHeaderUserIdIs1() throws Exception {
        when(snapshotQueryService.getLatest(anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(latestFixture());

        mockMvc.perform(get("/api/snapshot/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(snapshotQueryService).getLatest(1L, false, true);
    }

    @Test
    void latestDetail_forceIncludeDetailTrue() throws Exception {
        when(snapshotQueryService.getLatest(anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(latestFixture());

        mockMvc.perform(get("/api/snapshot/latest/detail").header("X-User-Id", "7"))
                .andExpect(status().isOk());
        verify(snapshotQueryService).getLatest(7L, true, true);
    }

    @Test
    void latest_nullServiceResult_returnsNullData() throws Exception {
        when(snapshotQueryService.getLatest(anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(null);

        mockMvc.perform(get("/api/snapshot/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void byDate_invalidDate_returnsServerError() throws Exception {
        // byDate 路径使用 ISO date；非法格式由 GlobalExceptionHandler 透传为 500
        mockMvc.perform(get("/api/snapshot/not-a-date")
                        .header("X-User-Id", "1"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void byDate_notFound_returnsCode2001() throws Exception {
        when(snapshotQueryService.getByDate(anyLong(), any(), anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND, "该日期无快照数据"));

        mockMvc.perform(get("/api/snapshot/2026-07-16")
                        .header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void byDate_returnsData() throws Exception {
        SnapshotByDateResponse resp = SnapshotByDateResponse.builder()
                .snapshotDate(LocalDate.of(2026, 7, 16))
                .sixCategoriesTotal(new BigDecimal("1595.73"))
                .balanceFund(new BigDecimal("418.46"))
                .totalAssetWithBalance(new BigDecimal("2014.19"))
                .build();
        when(snapshotQueryService.getByDate(anyLong(), any(), anyBoolean())).thenReturn(resp);

        mockMvc.perform(get("/api/snapshot/2026-07-16").header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.snapshotDate").value("2026-07-16"));
        verify(snapshotQueryService).getByDate(3L, LocalDate.of(2026, 7, 16), true);
    }

    @Test
    void history_defaultPage_returnsData() throws Exception {
        SnapshotHistoryResponse resp = SnapshotHistoryResponse.builder()
                .items(List.of(SnapshotHistoryItem.builder()
                        .snapshotDate(LocalDate.of(2026, 7, 16))
                        .sixCategoriesTotal(new BigDecimal("1595.73"))
                        .build()))
                .total(1L)
                .page(1)
                .pageSize(20)
                .build();
        when(snapshotQueryService.getHistory(anyLong(), any(), any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(resp);

        mockMvc.perform(get("/api/snapshot/history").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));
        verify(snapshotQueryService).getHistory(eq(1L), any(), any(), eq(1), eq(20), eq(true));
    }

    @Test
    void history_invalidPage_returnsError() throws Exception {
        // page=0 现在被 Controller 主动抛 BusinessException -> 500
        mockMvc.perform(get("/api/snapshot/history?page=0").header("X-User-Id", "1"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void history_userIsolation_passesHeader() throws Exception {
        when(snapshotQueryService.getHistory(anyLong(), any(), any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(SnapshotHistoryResponse.builder().items(List.of()).total(0).page(1).pageSize(20).build());

        mockMvc.perform(get("/api/snapshot/history").header("X-User-Id", "42"))
                .andExpect(status().isOk());
        verify(snapshotQueryService).getHistory(eq(42L), any(), any(), eq(1), eq(20), eq(true));
    }

    @Test
    void confirm_endpointStillDelegatesToConfirmService() throws Exception {
        // 空 body 也能成功（业务方法不会校验 body 字段）；这里只验证 Controller 会调用 confirm
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/snapshot/confirm")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        verify(snapShotConfirmService).confirm(any(com.fincontrol.dto.snapshot.SnapshotConfirmRequest.class));
    }
}

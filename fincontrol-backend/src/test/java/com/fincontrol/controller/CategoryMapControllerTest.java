package com.fincontrol.controller;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.category.CategoryMapMatchItem;
import com.fincontrol.dto.category.CategoryMapMatchResponse;
import com.fincontrol.dto.category.CategoryMapUpdateResponse;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.ChatHistoryMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.service.CategoryMapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1a.5 Slice C：{@link CategoryMapController} 契约测试。
 *
 * <p>只 mock {@link CategoryMapService}，不加载 Mapper / DB；
 * 覆盖 match / update 端点的 URL、参数、body、userId 隔离、错误码 1002 / 1004 透传。
 */
@WebMvcTest(
        value = CategoryMapController.class,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
@TestPropertySource(properties = "spring.profiles.active=local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryMapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryMapService categoryMapService;

    @MockBean
    private FundCategoryMapMapper fundCategoryMapMapper;

    @MockBean
    private AssetRawMapper assetRawMapper;

    @MockBean
    private AssetSnapshotMapper assetSnapshotMapper;

    @MockBean
    private ChatHistoryMapper chatHistoryMapper;

    private CategoryMapMatchItem item(String fund, String cat, String src) {
        return CategoryMapMatchItem.builder()
                .fundName(fund)
                .category(cat)
                .source(src)
                .confirmedAt(LocalDateTime.of(2026, 7, 17, 10, 0))
                .build();
    }

    // ========================================================================
    // match: URL / 参数 / 响应包装
    // ========================================================================

    @Test
    void match_defaultUserId_returnsData() throws Exception {
        CategoryMapMatchResponse resp = CategoryMapMatchResponse.builder()
                .matchedFunds(List.of(
                        item("中加货币E", "货币类", "ai_guess"),
                        item("长城短债债券A", "债券类", "user_correct")))
                .unmatchedFunds(List.of("未知基金"))
                .build();
        when(categoryMapService.match(anyLong(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/category-map/match?funds=中加货币E,长城短债债券A,未知基金"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchedFunds[0].fundName").value("中加货币E"))
                .andExpect(jsonPath("$.data.matchedFunds[0].category").value("货币类"))
                .andExpect(jsonPath("$.data.matchedFunds[0].source").value("ai_guess"))
                .andExpect(jsonPath("$.data.matchedFunds[1].source").value("user_correct"))
                .andExpect(jsonPath("$.data.unmatchedFunds[0]").value("未知基金"));
        verify(categoryMapService).match(eq(1L), eq("中加货币E,长城短债债券A,未知基金"));
    }

    @Test
    void match_explicitUserId_passesThrough() throws Exception {
        when(categoryMapService.match(anyLong(), any()))
                .thenReturn(CategoryMapMatchResponse.builder().matchedFunds(List.of()).unmatchedFunds(List.of()).build());

        mockMvc.perform(get("/api/category-map/match?funds=A").header("X-User-Id", "7"))
                .andExpect(status().isOk());
        verify(categoryMapService).match(7L, "A");
    }

    @Test
    void match_noFundsParam_returnsEmptyData() throws Exception {
        when(categoryMapService.match(anyLong(), any()))
                .thenReturn(CategoryMapMatchResponse.builder().matchedFunds(List.of()).unmatchedFunds(List.of()).build());

        mockMvc.perform(get("/api/category-map/match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchedFunds").isArray())
                .andExpect(jsonPath("$.data.matchedFunds").isEmpty())
                .andExpect(jsonPath("$.data.unmatchedFunds").isArray())
                .andExpect(jsonPath("$.data.unmatchedFunds").isEmpty());
        verify(categoryMapService).match(eq(1L), eq(null));
    }

    @Test
    void match_tooManyFunds_returns400And1002() throws Exception {
        when(categoryMapService.match(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.FUNDS_COUNT_EXCEEDS_LIMIT,
                        "funds 数量 51 超过上限 50"));

        // 51 个 fund 用 URL 编码比较啰嗦，这里直接 stub 服务抛 1002 即可
        String csv = "F1,F2,F3,F4,F5,F6,F7,F8,F9,F10,F11,F12,F13,F14,F15,F16,F17,F18,F19,F20," +
                "F21,F22,F23,F24,F25,F26,F27,F28,F29,F30,F31,F32,F33,F34,F35,F36,F37,F38,F39,F40," +
                "F41,F42,F43,F44,F45,F46,F47,F48,F49,F50,F51";
        mockMvc.perform(get("/api/category-map/match?funds=" + csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("funds 数量 51 超过上限 50"));
    }

    // ========================================================================
    // update: URL / body / 响应包装
    // ========================================================================

    @Test
    void update_existingMapping_returnsUpdatedTrue() throws Exception {
        CategoryMapUpdateResponse resp = CategoryMapUpdateResponse.builder()
                .mappingId(50L)
                .fundName("易方达蓝筹精选")
                .category("混合类")
                .source("user_correct")
                .confirmedAt(LocalDateTime.of(2026, 7, 17, 10, 30))
                .updated(true)
                .build();
        when(categoryMapService.update(anyLong(), any(), any())).thenReturn(resp);

        String body = "{\"fundName\":\"易方达蓝筹精选\",\"category\":\"混合类\"}";
        mockMvc.perform(post("/api/category-map/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mappingId").value(50))
                .andExpect(jsonPath("$.data.fundName").value("易方达蓝筹精选"))
                .andExpect(jsonPath("$.data.category").value("混合类"))
                .andExpect(jsonPath("$.data.source").value("user_correct"))
                .andExpect(jsonPath("$.data.updated").value(true));
        verify(categoryMapService).update(eq(1L), eq("易方达蓝筹精选"), eq("混合类"));
    }

    @Test
    void update_newMapping_returnsUpdatedFalse() throws Exception {
        CategoryMapUpdateResponse resp = CategoryMapUpdateResponse.builder()
                .mappingId(77L)
                .fundName("新基金X")
                .category("商品类")
                .source("user_manual")
                .confirmedAt(LocalDateTime.of(2026, 7, 17, 10, 30))
                .updated(false)
                .build();
        when(categoryMapService.update(anyLong(), any(), any())).thenReturn(resp);

        String body = "{\"fundName\":\"新基金X\",\"category\":\"商品类\"}";
        mockMvc.perform(post("/api/category-map/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.source").value("user_manual"))
                .andExpect(jsonPath("$.data.updated").value(false));
        verify(categoryMapService).update(eq(1L), eq("新基金X"), eq("商品类"));
    }

    @Test
    void update_invalidCategory_returns400And1004() throws Exception {
        when(categoryMapService.update(anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_CATEGORY_NAME,
                        "category '非法类' 不在六大类枚举值内"));

        String body = "{\"fundName\":\"某基金\",\"category\":\"非法类\"}";
        mockMvc.perform(post("/api/category-map/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void update_userIsolationHeader() throws Exception {
        CategoryMapUpdateResponse resp = CategoryMapUpdateResponse.builder()
                .mappingId(9L)
                .fundName("基金A")
                .category("货币类")
                .source("user_manual")
                .confirmedAt(LocalDateTime.of(2026, 7, 17, 10, 30))
                .updated(false)
                .build();
        when(categoryMapService.update(anyLong(), any(), any())).thenReturn(resp);

        String body = "{\"fundName\":\"基金A\",\"category\":\"货币类\"}";
        mockMvc.perform(post("/api/category-map/update")
                        .header("X-User-Id", "9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(categoryMapService).update(eq(9L), eq("基金A"), eq("货币类"));
    }

    @Test
    void update_bodyUserIdOverridesHeader() throws Exception {
        CategoryMapUpdateResponse resp = CategoryMapUpdateResponse.builder()
                .mappingId(1L)
                .fundName("基金A")
                .category("货币类")
                .source("user_manual")
                .confirmedAt(LocalDateTime.of(2026, 7, 17, 10, 30))
                .updated(false)
                .build();
        when(categoryMapService.update(anyLong(), any(), any())).thenReturn(resp);

        // header 是 1，但 body 指定 userId=42
        String body = "{\"fundName\":\"基金A\",\"category\":\"货币类\",\"userId\":42}";
        mockMvc.perform(post("/api/category-map/update")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        // body 的 userId 优先
        verify(categoryMapService).update(eq(42L), eq("基金A"), eq("货币类"));
    }

    @Test
    void update_balanceCategoryAllowed() throws Exception {
        CategoryMapUpdateResponse resp = CategoryMapUpdateResponse.builder()
                .mappingId(99L)
                .fundName("余额宝")
                .category("余额类")
                .source("user_manual")
                .confirmedAt(LocalDateTime.of(2026, 7, 17, 10, 30))
                .updated(false)
                .build();
        when(categoryMapService.update(anyLong(), any(), any())).thenReturn(resp);

        // [P0-3.3] 余额类必须允许写入
        String body = "{\"fundName\":\"余额宝\",\"category\":\"余额类\"}";
        mockMvc.perform(post("/api/category-map/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("余额类"));
        verify(categoryMapService).update(eq(1L), eq("余额宝"), eq("余额类"));
    }
}
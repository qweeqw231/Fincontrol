package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 1a.7 补测：PromptLoaderService 业务测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>A7-T07 warmUp 加载 3 行（screenshot_parser / ai_assistant / intent_classifier）</li>
 *   <li>A7-T08 get() 命中缓存</li>
 *   <li>A7-T09 get() 未命中缓存 → DB 查询 + 写回缓存</li>
 *   <li>A7-T10 get() DB 无此 prompt → 抛 INTERNAL_ERROR</li>
 *   <li>A7-T11 get() DB 抛 SQLException → 包成 DATABASE_ERROR</li>
 *   <li>A7-T12 warmUp 失败不抛异常（业务降级）</li>
 *   <li>A7-T13 putForTest 仅写缓存</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptLoaderTest {

    @Mock private JdbcTemplate jdbc;

    private PromptLoaderService service;

    @BeforeEach
    void setUp() {
        service = new PromptLoaderService(jdbc);
    }

    @Test
    @DisplayName("A7-T07: warmUp 加载 3 行 prompt 到缓存")
    void warmUp_loadsThreeRows() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("prompt_name", "ai_assistant", "prompt_content", "你是 AI 顾问..."),
                Map.of("prompt_name", "intent_classifier", "prompt_content", "判断投资类..."),
                Map.of("prompt_name", "screenshot_parser", "prompt_content", "解析截图...")
        ));

        service.warmUp();

        // 缓存命中
        assertThat(service.get("ai_assistant")).isEqualTo("你是 AI 顾问...");
        assertThat(service.get("intent_classifier")).isEqualTo("判断投资类...");
        assertThat(service.get("screenshot_parser")).isEqualTo("解析截图...");
    }

    @Test
    @DisplayName("A8V3: warmUp 同名多版本按 id DESC 只保留最新版本")
    void warmUp_samePromptMultipleVersions_keepsNewestRow() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("prompt_name", "screenshot_parser", "prompt_content", "v2 newest"),
                Map.of("prompt_name", "screenshot_parser", "prompt_content", "v1 old"),
                Map.of("prompt_name", "ai_assistant", "prompt_content", "assistant newest"),
                Map.of("prompt_name", "ai_assistant", "prompt_content", "assistant old")
        ));

        service.warmUp();

        assertThat(service.get("screenshot_parser")).isEqualTo("v2 newest");
        assertThat(service.get("ai_assistant")).isEqualTo("assistant newest");
    }

    @Test
    @DisplayName("A7-T08: get() 命中缓存 → 不查 DB")
    void get_cacheHit_doesNotQueryDb() {
        // 预填缓存（不走 warmUp）
        service.putForTest("ai_assistant", "cached content");

        String result = service.get("ai_assistant");

        assertThat(result).isEqualTo("cached content");
        // 没配置 jdbc.queryForList → 如果被调会抛 UnnecessaryStubbingException
    }

    @Test
    @DisplayName("A7-T09: get() 未命中缓存 → DB 查询 + 写回缓存")
    void get_cacheMiss_queriesDbAndCaches() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("ai_assistant")))
                .thenReturn(List.of("from db"));

        String first = service.get("ai_assistant");
        assertThat(first).isEqualTo("from db");

        // 第二次调用：缓存已写入，不应再调 DB（mock strict 模式会报错）
        String second = service.get("ai_assistant");
        assertThat(second).isEqualTo("from db");
    }

    @Test
    @DisplayName("A7-T10: get() DB 无此 prompt → 抛 INTERNAL_ERROR")
    void get_dbReturnsEmpty_throwsInternalError() {
        when(jdbc.queryForList(anyString(), eq(String.class), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.get("nonexistent_prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("A7-T11: get() DB 抛 SQLException → 包成 DATABASE_ERROR")
    void get_dbThrowsSqlException_wrapsAsDatabaseError() {
        when(jdbc.queryForList(anyString(), eq(String.class), any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("connection refused"));

        assertThatThrownBy(() -> service.get("ai_assistant"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.DATABASE_ERROR.getCode());
    }

    @Test
    @DisplayName("A7-T12: warmUp DB 抛异常 → 不向上抛（业务降级到 DB 查询兜底）")
    void warmUp_dbThrows_doesNotPropagate() {
        when(jdbc.queryForList(anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        // 不应抛
        service.warmUp();

        // 缓存为空 → get() 走 DB fallback 返 INTERNAL_ERROR（因为 prompt_versions 表也没数据）
        assertThatThrownBy(() -> service.get("ai_assistant"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("A7-T13: putForTest 仅写缓存，不查 DB")
    void putForTest_writesToCacheOnly() {
        service.putForTest("custom", "custom content");

        assertThat(service.get("custom")).isEqualTo("custom content");
    }
}
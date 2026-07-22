package com.fincontrol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.mapper.UserConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 1b.3 补救 R4：UserConfigService 读取 user_config.target_ratios 行为。
 */
@ExtendWith(MockitoExtension.class)
class UserConfigServiceTest {

    @Mock private UserConfigMapper userConfigMapper;

    private UserConfigService service;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new UserConfigService(userConfigMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("R4-T1: user_config 缺值时使用默认 10/15/25/25/20/5")
    void defaultValues_whenMissing() {
        when(userConfigMapper.selectValue(USER_ID, "target_ratios")).thenReturn(null);
        Map<String, BigDecimal> ratios = service.loadSixCategoryTargetRatios(USER_ID);
        assertThat(ratios).containsEntry("货币类", new BigDecimal("10"))
                .containsEntry("固收类", new BigDecimal("15"))
                .containsEntry("商品类", new BigDecimal("25"))
                .containsEntry("A股权益类", new BigDecimal("25"))
                .containsEntry("海外权益类", new BigDecimal("20"))
                .containsEntry("港股大中华类", new BigDecimal("5"));
    }

    @Test
    @DisplayName("R4-T2: 港股别名 key 也能 canonicalize")
    void aliasKey_canonicalize() {
        String json = "{\"货币类\":12,\"固收类\":13,\"商品类\":20,\"A股权益类\":30,\"海外权益类\":20,\"港股/大中华类\":5}";
        when(userConfigMapper.selectValue(USER_ID, "target_ratios")).thenReturn(json);
        Map<String, BigDecimal> ratios = service.loadSixCategoryTargetRatios(USER_ID);
        assertThat(ratios).containsEntry("港股大中华类", new BigDecimal("5"));
    }

    @Test
    @DisplayName("R4-T3: 合计非 100% 时 fallback 默认")
    void invalidSum_fallback() {
        // sum = 60+50+5+0+0+0 = 115 必然不为 100 → 触发 fallback
        String json = "{\"货币类\":60,\"固收类\":50,\"商品类\":5,\"A股权益类\":0,\"海外权益类\":0,\"港股大中华类\":0}";
        when(userConfigMapper.selectValue(USER_ID, "target_ratios")).thenReturn(json);
        Map<String, BigDecimal> ratios = service.loadSixCategoryTargetRatios(USER_ID);
        assertThat(ratios).containsEntry("货币类", new BigDecimal("10"))
                .containsEntry("固收类", new BigDecimal("15"));
    }

    @Test
    @DisplayName("R4-T4: 损坏 JSON 走 fallback")
    void brokenJson_fallback() {
        when(userConfigMapper.selectValue(USER_ID, "target_ratios")).thenReturn("{not json");
        Map<String, BigDecimal> ratios = service.loadSixCategoryTargetRatios(USER_ID);
        assertThat(ratios).containsEntry("商品类", new BigDecimal("25"));
    }
}

package com.fincontrol.service;

import com.fincontrol.entity.FundCategoryMap;
import com.fincontrol.mapper.FundCategoryMapMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.8.8 T-V3.2-02：FundCategoryResolver 优先级。
 *
 * <ul>
 *   <li>1. user_correct 命中 → canonical + isUserConfirmed=true</li>
 *   <li>2. ai_guess / user_manual 命中 → canonical + isUserConfirmed=false</li>
 *   <li>3. 未知名 fromAlias 命中 → canonical + isUserConfirmed=false</li>
 *   <li>4. fallback → raw + isUserConfirmed=false</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FundCategoryResolverTest {

    private static final Long USER_ID = 1L;

    @Mock
    private FundCategoryMapMapper fundCategoryMapMapper;

    @InjectMocks
    private FundCategoryResolver resolver;

    private FundCategoryMap row(String source, String category) {
        FundCategoryMap m = new FundCategoryMap();
        m.setId(1L);
        m.setUserId(USER_ID);
        m.setFundName("X");
        m.setCategory(category);
        m.setSource(source);
        m.setConfirmedAt(LocalDateTime.of(2026, 7, 18, 10, 0));
        return m;
    }

    // ========================================================================
    // 优先级 1: user_correct 命中
    // ========================================================================

    @Test
    @DisplayName("优先级1: user_correct 命中 → canonical + isUserConfirmed=true（同时透传 confirmedAt）")
    void resolve_userCorrectWins() {
        LocalDateTime confirmed = LocalDateTime.of(2026, 7, 15, 12, 30);
        FundCategoryMap hit = row("user_correct", "海外权益类");
        hit.setConfirmedAt(confirmed);
        when(fundCategoryMapMapper.selectByUserCorrect(USER_ID, "国泰黄金C"))
                .thenReturn(hit);

        FundCategoryResolver.ResolvedCategory r = resolver.resolve("国泰黄金C", "QDII", USER_ID);

        assertThat(r.canonicalName()).isEqualTo("海外权益类");
        assertThat(r.isUserConfirmed()).isTrue();
        assertThat(r.confirmedAt()).isEqualTo(confirmed);
        verify(fundCategoryMapMapper, never()).selectByUserAndFundName(anyLong(), any());
    }

    // ========================================================================
    // 优先级 2: ai_guess / user_manual 命中
    // ========================================================================

    @Test
    @DisplayName("优先级2: ai_guess 命中 → canonical + isUserConfirmed=false")
    void resolve_aiGuessFallback() {
        when(fundCategoryMapMapper.selectByUserCorrect(USER_ID, "新基金X"))
                .thenReturn(null);
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "新基金X"))
                .thenReturn(row("ai_guess", "商品类"));

        FundCategoryResolver.ResolvedCategory r = resolver.resolve("新基金X", "其他", USER_ID);

        assertThat(r.canonicalName()).isEqualTo("商品类");
        assertThat(r.isUserConfirmed()).isFalse();
    }

    @Test
    @DisplayName("优先级2: user_manual 命中 → canonical + isUserConfirmed=false")
    void resolve_userManualFallback() {
        when(fundCategoryMapMapper.selectByUserCorrect(USER_ID, "新基金Y"))
                .thenReturn(null);
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "新基金Y"))
                .thenReturn(row("user_manual", "固收类"));

        FundCategoryResolver.ResolvedCategory r = resolver.resolve("新基金Y", "其他", USER_ID);

        assertThat(r.canonicalName()).isEqualTo("固收类");
        assertThat(r.isUserConfirmed()).isFalse();
    }

    // ========================================================================
    // 优先级 3: fromAlias 命中（fund_category_map 无任何行）
    // ========================================================================

    @Test
    @DisplayName("优先级3: 完全陌生 fund + raw='QDII' → fromAlias 归一化为海外权益类")
    void resolve_fromAliasFallback() {
        when(fundCategoryMapMapper.selectByUserCorrect(USER_ID, "完全陌生Y"))
                .thenReturn(null);
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "完全陌生Y"))
                .thenReturn(null);

        FundCategoryResolver.ResolvedCategory r = resolver.resolve("完全陌生Y", "QDII", USER_ID);

        assertThat(r.canonicalName()).isEqualTo("海外权益类");
        assertThat(r.isUserConfirmed()).isFalse();
    }

    // ========================================================================
    // 优先级 4: 都未命中 → raw 兜底
    // ========================================================================

    @Test
    @DisplayName("优先级4: 都未命中 → raw 兜底 + isUserConfirmed=false")
    void resolve_rawFallback() {
        when(fundCategoryMapMapper.selectByUserCorrect(USER_ID, "完全陌生Z"))
                .thenReturn(null);
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "完全陌生Z"))
                .thenReturn(null);

        FundCategoryResolver.ResolvedCategory r = resolver.resolve("完全陌生Z", "其他/新奇分类", USER_ID);

        assertThat(r.canonicalName()).isEqualTo("其他/新奇分类");
        assertThat(r.isUserConfirmed()).isFalse();
    }

    @Test
    @DisplayName("优先级4: 都未命中 + raw=null → 兜底「其他」")
    void resolve_rawNullBecomesOther() {
        when(fundCategoryMapMapper.selectByUserCorrect(USER_ID, "完全陌生W"))
                .thenReturn(null);
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "完全陌生W"))
                .thenReturn(null);

        FundCategoryResolver.ResolvedCategory r = resolver.resolve("完全陌生W", null, USER_ID);
        assertThat(r.canonicalName()).isEqualTo("其他");
    }

    // ========================================================================
    // 边界
    // ========================================================================

    @Test
    @DisplayName("fundName=null → 返 raw 兜底（不调 mapper）")
    void resolve_nullFundName() {
        FundCategoryResolver.ResolvedCategory r = resolver.resolve(null, "QDII", USER_ID);
        assertThat(r.canonicalName()).isEqualTo("QDII");  // raw 兜底
        assertThat(r.isUserConfirmed()).isFalse();
        verify(fundCategoryMapMapper, never()).selectByUserCorrect(anyLong(), any());
    }

    @Test
    @DisplayName("fundName=blank → 返 raw 兜底（不调 mapper）")
    void resolve_blankFundName() {
        FundCategoryResolver.ResolvedCategory r = resolver.resolve("   ", "QDII", USER_ID);
        assertThat(r.canonicalName()).isEqualTo("QDII");
        verify(fundCategoryMapMapper, never()).selectByUserCorrect(anyLong(), any());
    }

    @Test
    @DisplayName("userId=null → 抛 IllegalArgumentException")
    void resolve_nullUserId() {
        assertThatThrownBy(() -> resolver.resolve("某基金", "QDII", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    // ========================================================================
    // listStale
    // ========================================================================

    @Test
    @DisplayName("listStale: 透传 cutoffDate = now - days")
    void listStale_passesCutoffDate() {
        when(fundCategoryMapMapper.selectStaleByUser(any(), any(LocalDateTime.class)))
                .thenReturn(java.util.List.of());

        java.time.LocalDateTime before = java.time.LocalDateTime.now();
        java.util.List<FundCategoryMap> out = resolver.listStale(USER_ID, 60);
        assertThat(out).isEmpty();

        org.mockito.ArgumentCaptor<java.time.LocalDateTime> cap =
                org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(fundCategoryMapMapper).selectStaleByUser(org.mockito.ArgumentMatchers.eq(USER_ID), cap.capture());
        // cutoff ≈ now - 60d
        long diffDays = java.time.Duration.between(cap.getValue(), before).toDays();
        assertThat(Math.abs(diffDays - 60)).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("listStale: days < 0 → 抛 IllegalArgumentException")
    void listStale_negativeDays() {
        assertThatThrownBy(() -> resolver.listStale(USER_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
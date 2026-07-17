package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.category.CategoryMapMatchResponse;
import com.fincontrol.dto.category.CategoryMapUpdateResponse;
import com.fincontrol.entity.FundCategoryMap;
import com.fincontrol.mapper.FundCategoryMapMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.5 Slice A/B：{@link CategoryMapService} 业务测试（A5-S01–A5-S06 + 4 边界）。
 *
 * <p>替身：Mapper mock（[2026-07-17_phase1a5-acceptance-plan.md §2](#) 锁定的 Service mock + Mapper mock 层级）。
 */
@ExtendWith(MockitoExtension.class)
class CategoryMapServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_ID_2 = 2L;

    @Mock
    private FundCategoryMapMapper fundCategoryMapMapper;

    @InjectMocks
    private CategoryMapService service;

    private FundCategoryMap row(Long id, Long userId, String fundName, String category, String source,
                                LocalDateTime confirmedAt) {
        FundCategoryMap m = new FundCategoryMap();
        m.setId(id);
        m.setUserId(userId);
        m.setFundName(fundName);
        m.setCategory(category);
        m.setSource(source);
        m.setConfirmedAt(confirmedAt);
        return m;
    }

    // ========================================================================
    // A5-S01: match 全部命中
    // ========================================================================

    @Test
    @DisplayName("A5-S01: match 全部命中 → matchedFunds 全列，unmatchedFunds=[]")
    void match_allHit() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 17, 10, 0);
        List<FundCategoryMap> rows = List.of(
                row(101L, USER_ID, "中加货币E", "货币类", "ai_guess", t),
                row(102L, USER_ID, "长城短债债券A", "债券类", "user_correct", t)
        );
        when(fundCategoryMapMapper.selectByUserAndFundNames(eq(USER_ID), any(Collection.class)))
                .thenReturn(rows);

        CategoryMapMatchResponse resp = service.match(USER_ID, "中加货币E,长城短债债券A");

        assertThat(resp.getMatchedFunds()).hasSize(2);
        assertThat(resp.getUnmatchedFunds()).isEmpty();
        assertThat(resp.getMatchedFunds().get(0).getFundName()).isEqualTo("中加货币E");
        assertThat(resp.getMatchedFunds().get(0).getCategory()).isEqualTo("货币类");
        assertThat(resp.getMatchedFunds().get(0).getSource()).isEqualTo("ai_guess");
        assertThat(resp.getMatchedFunds().get(0).getConfirmedAt()).isEqualTo(t);
        assertThat(resp.getMatchedFunds().get(1).getSource()).isEqualTo("user_correct");
    }

    // ========================================================================
    // A5-S02: match 部分未命中
    // ========================================================================

    @Test
    @DisplayName("A5-S02: match 部分未命中 → 缺的 fund 进 unmatchedFunds，顺序按输入")
    void match_partialMiss() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 17, 10, 0);
        // 只 mock 了 "中加货币E"，"未知基金" 不在结果中
        when(fundCategoryMapMapper.selectByUserAndFundNames(eq(USER_ID), any(Collection.class)))
                .thenReturn(List.of(row(101L, USER_ID, "中加货币E", "货币类", "ai_guess", t)));

        CategoryMapMatchResponse resp = service.match(USER_ID, "未知基金,中加货币E");

        assertThat(resp.getMatchedFunds()).hasSize(1);
        assertThat(resp.getMatchedFunds().get(0).getFundName()).isEqualTo("中加货币E");
        assertThat(resp.getUnmatchedFunds()).hasSize(1);
        // 顺序按输入
        assertThat(resp.getUnmatchedFunds().get(0)).isEqualTo("未知基金");
    }

    // ========================================================================
    // A5-S03: match 上限 50
    // ========================================================================

    @Test
    @DisplayName("A5-S03: match 50 个 fund 通过；51 个抛 1002")
    void match_limitBoundary() {
        // 51 个基金 → 抛 1002
        String csv51 = IntStream.rangeClosed(1, 51).mapToObj(i -> "FUND_" + i)
                .collect(Collectors.joining(","));
        assertThatThrownBy(() -> service.match(USER_ID, csv51))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.FUNDS_COUNT_EXCEEDS_LIMIT.getCode());

        // 50 个基金 → 通过（不抛异常）
        String csv50 = IntStream.rangeClosed(1, 50).mapToObj(i -> "FUND_" + i)
                .collect(Collectors.joining(","));
        when(fundCategoryMapMapper.selectByUserAndFundNames(eq(USER_ID), any(Collection.class)))
                .thenReturn(Collections.emptyList());
        CategoryMapMatchResponse resp50 = service.match(USER_ID, csv50);
        assertThat(resp50.getMatchedFunds()).isEmpty();
        assertThat(resp50.getUnmatchedFunds()).hasSize(50);
        verify(fundCategoryMapMapper).selectByUserAndFundNames(eq(USER_ID), any(Collection.class));
    }

    // ========================================================================
    // A5-S04: update 已存在 → UPDATE（source='user_correct'）
    // ========================================================================

    @Test
    @DisplayName("A5-S04: update 已存在 → UPDATE 路径，source='user_correct'，updated=true")
    void update_existing() {
        LocalDateTime before = LocalDateTime.of(2026, 7, 17, 9, 0);
        LocalDateTime after = LocalDateTime.of(2026, 7, 17, 10, 30);
        FundCategoryMap existing = row(50L, USER_ID, "易方达蓝筹精选", "股票类", "ai_guess", before);
        FundCategoryMap afterWrite = row(50L, USER_ID, "易方达蓝筹精选", "混合类", "user_correct", after);

        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "易方达蓝筹精选"))
                .thenReturn(existing)   // 第一次：决定 isUpdate
                .thenReturn(afterWrite); // 第二次：回读 confirmedAt

        CategoryMapUpdateResponse resp = service.update(USER_ID, "易方达蓝筹精选", "混合类");

        assertThat(resp.isUpdated()).isTrue();
        assertThat(resp.getMappingId()).isEqualTo(50L);
        assertThat(resp.getSource()).isEqualTo("user_correct");
        assertThat(resp.getCategory()).isEqualTo("混合类");
        assertThat(resp.getConfirmedAt()).isEqualTo(after);

        // 验证 upsertByFundName 入参 source='user_correct'
        ArgumentCaptor<FundCategoryMap> captor = ArgumentCaptor.forClass(FundCategoryMap.class);
        verify(fundCategoryMapMapper).upsertByFundName(captor.capture());
        FundCategoryMap sent = captor.getValue();
        assertThat(sent.getSource()).isEqualTo("user_correct");
        assertThat(sent.getCategory()).isEqualTo("混合类");
        assertThat(sent.getFundName()).isEqualTo("易方达蓝筹精选");
        assertThat(sent.getUserId()).isEqualTo(USER_ID);
    }

    // ========================================================================
    // A5-S05: update 不存在 → INSERT（source='user_manual'）
    // ========================================================================

    @Test
    @DisplayName("A5-S05: update 不存在 → INSERT 路径，source='user_manual'，updated=false")
    void update_newInsert() {
        LocalDateTime after = LocalDateTime.of(2026, 7, 17, 10, 30);
        FundCategoryMap newRow = row(77L, USER_ID, "新基金X", "商品类", "user_manual", after);

        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "新基金X"))
                .thenReturn(null)
                .thenReturn(newRow);

        CategoryMapUpdateResponse resp = service.update(USER_ID, "新基金X", "商品类");

        assertThat(resp.isUpdated()).isFalse();
        assertThat(resp.getMappingId()).isEqualTo(77L);
        assertThat(resp.getSource()).isEqualTo("user_manual");
        assertThat(resp.getCategory()).isEqualTo("商品类");

        // 验证 upsertByFundName 入参 source='user_manual'
        ArgumentCaptor<FundCategoryMap> captor = ArgumentCaptor.forClass(FundCategoryMap.class);
        verify(fundCategoryMapMapper).upsertByFundName(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("user_manual");
    }

    // ========================================================================
    // A5-S06: update userId 隔离
    // ========================================================================

    @Test
    @DisplayName("A5-S06: update userId 隔离 — user1 写不影响 user2")
    void update_userIsolation() {
        LocalDateTime after = LocalDateTime.of(2026, 7, 17, 10, 30);
        FundCategoryMap user1Row = row(11L, USER_ID, "基金A", "货币类", "user_manual", after);

        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "基金A"))
                .thenReturn(null).thenReturn(user1Row);

        service.update(USER_ID, "基金A", "货币类");

        // 验证 user1 的写入 userId 是 1
        ArgumentCaptor<FundCategoryMap> captor = ArgumentCaptor.forClass(FundCategoryMap.class);
        verify(fundCategoryMapMapper).upsertByFundName(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getUserId()).isNotEqualTo(USER_ID_2);

        // 验证 selectByUserAndFundName 两次调用用的都是 user1（UPDATE 路径会查两次：是否存在 + 回读）
        verify(fundCategoryMapMapper, org.mockito.Mockito.times(2))
                .selectByUserAndFundName(USER_ID, "基金A");
    }

    // ========================================================================
    // 边界 1: match 空 CSV
    // ========================================================================

    @Test
    @DisplayName("边界 1: match 空 CSV → matched=[] + unmatched=[]，不抛异常")
    void match_emptyCsv() {
        CategoryMapMatchResponse resp1 = service.match(USER_ID, null);
        assertThat(resp1.getMatchedFunds()).isEmpty();
        assertThat(resp1.getUnmatchedFunds()).isEmpty();

        CategoryMapMatchResponse resp2 = service.match(USER_ID, "");
        assertThat(resp2.getMatchedFunds()).isEmpty();
        assertThat(resp2.getUnmatchedFunds()).isEmpty();

        CategoryMapMatchResponse resp3 = service.match(USER_ID, "  , , , ");
        assertThat(resp3.getMatchedFunds()).isEmpty();
        assertThat(resp3.getUnmatchedFunds()).isEmpty();

        // 不应调 mapper
        verify(fundCategoryMapMapper, never()).selectByUserAndFundNames(anyLong(), any(Collection.class));
    }

    // ========================================================================
    // 边界 2: match 去重 + trim
    // ========================================================================

    @Test
    @DisplayName("边界 2: match 去重 + trim — 'A,A,B, ,' → 查 A、B")
    void match_dedupeAndTrim() {
        when(fundCategoryMapMapper.selectByUserAndFundNames(eq(USER_ID), any(Collection.class)))
                .thenReturn(Collections.emptyList());

        service.match(USER_ID, " A , A , B , , ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(fundCategoryMapMapper).selectByUserAndFundNames(eq(USER_ID), captor.capture());
        Collection<String> queried = captor.getValue();
        assertThat(queried).containsExactlyInAnyOrder("A", "B");
        assertThat(queried).hasSize(2);
    }

    // ========================================================================
    // 边界 3: update 非法大类 → 1004，不调 mapper
    // ========================================================================

    @Test
    @DisplayName("边界 3: update category='非法类' → 抛 1004，不调 mapper")
    void update_invalidCategory() {
        assertThatThrownBy(() -> service.update(USER_ID, "某基金", "非法类"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_CATEGORY_NAME.getCode());

        verify(fundCategoryMapMapper, never()).selectByUserAndFundName(anyLong(), any());
        verify(fundCategoryMapMapper, never()).upsertByFundName(any());
    }

    // ========================================================================
    // 边界 4: update 空 fundName → 1004，不调 mapper
    // ========================================================================

    @Test
    @DisplayName("边界 4: update 空 fundName → 抛 1004，不调 mapper")
    void update_emptyFundName() {
        assertThatThrownBy(() -> service.update(USER_ID, " ", "货币类"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_CATEGORY_NAME.getCode());

        assertThatThrownBy(() -> service.update(USER_ID, null, "货币类"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.INVALID_CATEGORY_NAME.getCode());

        verify(fundCategoryMapMapper, never()).upsertByFundName(any());
    }

    // ========================================================================
    // 工具方法白盒测试：parseFundsCsv
    // ========================================================================

    @Test
    @DisplayName("parseFundsCsv: null / 空 / 纯空白 → 空列表")
    void parseFundsCsv_emptyInputs() {
        assertThat(CategoryMapService.parseFundsCsv(null)).isEmpty();
        assertThat(CategoryMapService.parseFundsCsv("")).isEmpty();
        assertThat(CategoryMapService.parseFundsCsv("   ")).isEmpty();
        assertThat(CategoryMapService.parseFundsCsv(",,, ,,")).isEmpty();
    }

    @Test
    @DisplayName("parseFundsCsv: 保序去重 + trim")
    void parseFundsCsv_orderPreserving() {
        List<String> out = CategoryMapService.parseFundsCsv("B,A , A ,C, B");
        assertThat(out).containsExactly("B", "A", "C");
    }

    // ========================================================================
    // 防御性测试：余额类允许写入（[P0-3.3]）
    // ========================================================================

    @Test
    @DisplayName("防御: update category='余额类' 不被 1004 拦截（[P0-3.3]）")
    void update_balanceCategoryAllowed() {
        LocalDateTime after = LocalDateTime.of(2026, 7, 17, 10, 30);
        FundCategoryMap balanceRow = row(99L, USER_ID, "余额宝", "余额类", "user_manual", after);

        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "余额宝"))
                .thenReturn(null).thenReturn(balanceRow);

        CategoryMapUpdateResponse resp = service.update(USER_ID, "余额宝", "余额类");
        assertThat(resp.getCategory()).isEqualTo("余额类");
        assertThat(resp.getSource()).isEqualTo("user_manual");

        // 防止回归：1004 错误码不应出现 → 业务侧应能成功 upsert
        verify(fundCategoryMapMapper).upsertByFundName(any(FundCategoryMap.class));
    }
}
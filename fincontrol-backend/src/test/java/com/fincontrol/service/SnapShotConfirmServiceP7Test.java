package com.fincontrol.service;

import com.fincontrol.dto.screenshot.ParsedAsset;
import com.fincontrol.dto.screenshot.ParsedAsset.CategoryBlock;
import com.fincontrol.dto.screenshot.ParsedAsset.FundLine;
import com.fincontrol.dto.snapshot.SnapshotConfirmRequest;
import com.fincontrol.dto.snapshot.SnapshotConfirmResult;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.entity.FundCategoryMap;
import com.fincontrol.entity.SnapshotMeta;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.FundCategoryMapMapper;
import com.fincontrol.mapper.SnapshotMetaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1b.3 P7 修复回归测试：writeFundCategoryMap 不再把 ai_guess 错误地升级为 user_correct。
 *
 * <p>背景：之前 confirm 流程的规则是 {@code existing == null ? "ai_guess" : "user_correct"}，
 * 这导致 AI 一次错误分类被下次 confirm 永远覆盖（错误"固化"为"用户已确认"）。
 * 修复后规则：
 * <ul>
 *   <li>existing == null → source='ai_guess'（首次入库）</li>
 *   <li>existing.source == 'ai_guess' → 保持 'ai_guess'，**不**自动升级</li>
 *   <li>existing.source == 'user_correct' / 'user_manual' → 保持原 source（明确用户映射）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnapShotConfirmServiceP7Test {

    @Mock private AssetRawMapper assetRawMapper;
    @Mock private AssetSnapshotMapper assetSnapshotMapper;
    @Mock private FundCategoryMapMapper fundCategoryMapMapper;
    @Mock private SnapshotMetaMapper snapshotMetaMapper;
    @Mock private SettingsService settingsService; // PR3plus 决策 30/31

    @InjectMocks private DedupEngine dedupEngine = new DedupEngine();

    private SnapShotConfirmService service;

    private static final long USER_ID = 1L;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 22);

    @BeforeEach
    void setUp() {
        service = new SnapShotConfirmService(
                assetRawMapper, assetSnapshotMapper, fundCategoryMapMapper,
                dedupEngine, snapshotMetaMapper, settingsService);
        // 1a.3 镜像校验：写新批次前先翻 is_latest（无 existing 行时返回 0）
        when(assetRawMapper.updateIsLatestBySnapshotDate(anyLong(), any(LocalDate.class))).thenReturn(0);
        // 维度 E 检测：本测试不需要 existing fund
        when(fundCategoryMapMapper.selectFundNamesByUserAndSnapshotDate(anyLong(), any(LocalDate.class)))
                .thenReturn(Collections.emptySet());
        // AssetRawMapper.insert 桩（无返回值 void）
        // 1a.4 镜像校验：维度 D 模拟返回测试请求中的 catTotal（=100.00），与 verifyMirror 期望一致。
        when(assetRawMapper.sumAmountByUserAndDateAndCategory(anyLong(), any(LocalDate.class), any()))
                .thenReturn(new BigDecimal("100.00"));
        when(assetSnapshotMapper.countLatestByUserAndDateAndCategory(anyLong(), any(LocalDate.class), any()))
                .thenReturn(1);
        // PR3plus 决策 30/31：默认 7 天限制（testDate 7/22 < 7/24 不会超）
        when(settingsService.getMaxSnapshotAgeDays(anyLong())).thenReturn(7);
    }

    // ========================================================================
    // 修复核心：writeFundCategoryMap 不会让 ai_guess 错误升级
    // ========================================================================

    @Test
    @DisplayName("P7-R1 · existing==null → 写入 source='ai_guess'（首次入库）")
    void writeFundCategoryMap_existingNull_writesAiGuess() {
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "长城短债债券A"))
                .thenReturn(null);

        ArgumentCaptor<FundCategoryMap> captor = ArgumentCaptor.forClass(FundCategoryMap.class);

        service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));

        verify(fundCategoryMapMapper, times(1)).upsertByFundName(captor.capture());
        FundCategoryMap written = captor.getValue();
        assertThat(written.getCategory()).isEqualTo("固收类");
        assertThat(written.getSource()).isEqualTo("ai_guess");
    }

    @Test
    @DisplayName("P7-R2 · existing.source='ai_guess' → 保持 'ai_guess'，不升级为 user_correct（核心修复）")
    void writeFundCategoryMap_existingAiGuess_keepsAiGuess() {
        FundCategoryMap existing = map("长城短债债券A", "A股权益类", "ai_guess");
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "长城短债债券A"))
                .thenReturn(existing);

        ArgumentCaptor<FundCategoryMap> captor = ArgumentCaptor.forClass(FundCategoryMap.class);

        service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));

        verify(fundCategoryMapMapper, times(1)).upsertByFundName(captor.capture());
        FundCategoryMap written = captor.getValue();
        assertThat(written.getCategory()).isEqualTo("固收类");
        // 关键断言：source 保持 ai_guess，不被"自动确认"成 user_correct
        assertThat(written.getSource()).isEqualTo("ai_guess");
    }

    @Test
    @DisplayName("P7-R3 · existing.source='user_correct' → 保持 user_correct（用户明确纠正仍优先）")
    void writeFundCategoryMap_existingUserCorrect_keepsUserCorrect() {
        FundCategoryMap existing = map("天弘纳指100", "海外权益类", "user_correct");
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "天弘纳指100"))
                .thenReturn(existing);

        ArgumentCaptor<FundCategoryMap> captor = ArgumentCaptor.forClass(FundCategoryMap.class);

        // 假设 AI 这次返回"商品类"（错误），但用户上次已确认是海外权益类
        service.confirm(reqWithSingleFund("天弘纳指100", "商品类"));

        verify(fundCategoryMapMapper, times(1)).upsertByFundName(captor.capture());
        FundCategoryMap written = captor.getValue();
        // 关键断言：user_correct 的 user 明确意图不能被 AI 覆盖
        assertThat(written.getCategory()).isEqualTo("海外权益类");
        assertThat(written.getSource()).isEqualTo("user_correct");
    }

    @Test
    @DisplayName("P7-R4 · existing.source='user_manual' → 保持 user_manual")
    void writeFundCategoryMap_existingUserManual_keepsUserManual() {
        FundCategoryMap existing = map("余额宝", "余额类", "user_manual");
        when(fundCategoryMapMapper.selectByUserAndFundName(USER_ID, "余额宝"))
                .thenReturn(existing);

        ArgumentCaptor<FundCategoryMap> captor = ArgumentCaptor.forClass(FundCategoryMap.class);

        service.confirm(reqWithSingleFund("余额宝", "其他类"));

        verify(fundCategoryMapMapper, times(1)).upsertByFundName(captor.capture());
        FundCategoryMap written = captor.getValue();
        assertThat(written.getCategory()).isEqualTo("余额类");
        assertThat(written.getSource()).isEqualTo("user_manual");
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private static FundCategoryMap map(String fundName, String category, String source) {
        FundCategoryMap m = new FundCategoryMap();
        m.setUserId(USER_ID);
        m.setFundName(fundName);
        m.setCategory(category);
        m.setSource(source);
        m.setConfirmedAt(java.time.LocalDateTime.now());
        m.setLastSeenAt(java.time.LocalDateTime.now());
        return m;
    }

    private SnapshotConfirmRequest reqWithSingleFund(String fundName, String category) {
        ParsedAsset asset = new ParsedAsset();
        asset.setConversationId("conv-test");
        asset.setSnapshotDate(TEST_DATE.toString());
        FundLine fl = new FundLine();
        fl.setFundName(fundName);
        fl.setAmount(new BigDecimal("100.00"));
        fl.setProfit(new BigDecimal("1.00"));
        fl.setHoldingProfit(new BigDecimal("1.00"));
        fl.setCumulativeProfit(new BigDecimal("1.00"));
        CategoryBlock cb = new CategoryBlock();
        cb.setCategoryName(category);
        cb.setFunds(new ArrayList<>(List.of(fl)));
        cb.setCategoryTotal(new BigDecimal("100.00"));
        asset.setCategories(new ArrayList<>(List.of(cb)));
        asset.setMatchedFunds(new ArrayList<>(List.of(fundName)));
        asset.setUnmatchedFunds(new ArrayList<>());

        SnapshotConfirmRequest req = new SnapshotConfirmRequest();
        req.setUserId(USER_ID);
        req.setSnapshotDate(TEST_DATE);
        req.setConfirmedOverwrite(true);
        req.setParsedAssets(List.of(asset));
        return req;
    }
}

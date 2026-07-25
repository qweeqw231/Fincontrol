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
import static org.mockito.ArgumentMatchers.eq;
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
        // 决策 33 D4：writeAssetSnapshot 同样需要在循环前清旧行
        when(assetSnapshotMapper.updateIsLatestBySnapshotDate(anyLong(), any(LocalDate.class))).thenReturn(0);
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
    // 1b.4-pr7 (DATA-016) Fix 4：二次 confirm 同 snapshot_date 不报 500（幂等 overwrite）
    // ========================================================================

    /**
     * B9：同 snapshot_date 二次 confirm → 不调 BaseMapper.insert（避免 PK 冲突），
     *     改调 upsertByFundName（ON DUPLICATE KEY UPDATE）。
     */
    @Test
    @DisplayName("Fix 4-B9 · 二次 confirm 同 snapshot_date 调用 upsertByFundName 而非 insert（避免 500）")
    void confirmSecondTime_usesUpsertNotInsert() {
        // 首次 + 二次 confirm 都是同 fund + 同 date
        service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));
        service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));

        // 二次 confirm 不应再调 BaseMapper.insert（那是导致 500 的根因）
        verify(assetRawMapper, never()).insert(any(AssetRaw.class));
        // 应改调 upsertByFundName（首次 + 二次 = 2 次）
        verify(assetRawMapper, times(2)).upsertByFundName(any(AssetRaw.class));
    }

    /**
     * B10：二次 confirm 后，asset_raw.amount 应是新批次值（ON DUPLICATE KEY UPDATE 覆盖 amount）。
     *     二次 confirm 时 categoryTotal 变化，镜像校验读 sumAmount 应返新值。
     */
    @Test
    @DisplayName("Fix 4-B10 · 二次 confirm amount=新批次值（ON DUPLICATE KEY UPDATE 覆盖）")
    void confirmSecondTime_overridesAmount() {
        // 覆盖 setUp 的 stub：首次 confirm 返回 100.00，二次 confirm 返回 200.00
        // （镜像校验按调用顺序取 .thenReturn 链）
        when(assetRawMapper.sumAmountByUserAndDateAndCategory(anyLong(), any(LocalDate.class), any()))
                .thenReturn(new BigDecimal("100.00"))
                .thenReturn(new BigDecimal("200.00"));

        // 首次 confirm：amount=100.00
        service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));

        // 二次 confirm：amount 改为 200.00（模拟重新解析的金额）
        ParsedAsset asset = new ParsedAsset();
        asset.setConversationId("conv-test-2");
        asset.setSnapshotDate(TEST_DATE.toString());
        FundLine fl = new FundLine();
        fl.setFundName("长城短债债券A");
        fl.setAmount(new BigDecimal("200.00"));
        fl.setHoldingProfit(new BigDecimal("2.00"));
        fl.setCumulativeProfit(new BigDecimal("2.00"));
        CategoryBlock cb = new CategoryBlock();
        cb.setCategoryName("固收类");
        cb.setFunds(new ArrayList<>(List.of(fl)));
        cb.setCategoryTotal(new BigDecimal("200.00"));
        asset.setCategories(new ArrayList<>(List.of(cb)));
        asset.setMatchedFunds(new ArrayList<>(List.of("长城短债债券A")));
        asset.setUnmatchedFunds(new ArrayList<>());

        SnapshotConfirmRequest req2 = new SnapshotConfirmRequest();
        req2.setUserId(USER_ID);
        req2.setSnapshotDate(TEST_DATE);
        req2.setConfirmedOverwrite(true);
        req2.setParsedAssets(List.of(asset));
        service.confirm(req2);

        // 验证第二次 upsertByFundName 调用的 amount = 200.00
        ArgumentCaptor<AssetRaw> captor = ArgumentCaptor.forClass(AssetRaw.class);
        verify(assetRawMapper, times(2)).upsertByFundName(captor.capture());
        List<AssetRaw> allUpserts = captor.getAllValues();
        assertThat(allUpserts.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(allUpserts.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    /**
     * B11：二次 confirm 后镜像校验仍然通过（asset_raw 写入后与 asset_snapshot 镜像一致）。
     * 本质上 verifyMirror 不会报错 → @Transactional 不会回滚 → confirm 返回成功。
     */
    @Test
    @DisplayName("Fix 4-B11 · 二次 confirm 后镜像校验仍通过（不抛 INTERNAL_ERROR）")
    void confirmSecondTime_verifyMirrorPasses() {
        // 首次 + 二次 confirm 都是同 fund + 同 date
        SnapshotConfirmResult result1 = service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));
        SnapshotConfirmResult result2 = service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));

        // 两次都成功（未抛异常、未被 @Transactional 回滚）
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        // 验证镜像校验 3 个查询都被调过
        verify(assetRawMapper, times(2)).sumAmountByUserAndDateAndCategory(anyLong(), any(LocalDate.class), any());
        verify(assetSnapshotMapper, times(2)).countLatestByUserAndDateAndCategory(anyLong(), any(LocalDate.class), any());
    }

    // ========================================================================
    // 1b.4-pr7 (DATA-016) Fix 5：asset_snapshot + fund_category_map 幂等 DELETE
    // ========================================================================

    /**
     * B12：writeAssetSnapshot 头调 {@code deleteByUserAndDateAndCategory} 清理 unique key。
     *     否则 asset_snapshot.uk_user_date_category (3 列不含 is_latest) 会在二次 INSERT 时撞 Duplicate。
     */
    @Test
    @DisplayName("Fix 5-B12 · writeAssetSnapshot 头调 deleteByUserAndDateAndCategory 清扫 unique key")
    void writeAssetSnapshot_deletesByUserAndDateAndCategory() {
        service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));

        // writeAssetSnapshot 头调 1 次 deleteByUserAndDateAndCategory（单 category）
        verify(assetSnapshotMapper, times(1))
                .deleteByUserAndDateAndCategory(anyLong(), any(LocalDate.class), eq("固收类"));
    }

    /**
     * B13：writeFundCategoryMap 头调 {@code deleteByUserAndFundName}（复用 1a.8.8 接口）。
     *     fund_category_map.uk_user_fund (user_id, fund_name) 也会在二次 confirm 时撞 unique key。
     */
    @Test
    @DisplayName("Fix 5-B13 · writeFundCategoryMap 头调 deleteByUserAndFundName 清扫 unique key")
    void writeFundCategoryMap_deletesByUserAndFundName() {
        service.confirm(reqWithSingleFund("长城短债债券A", "固收类"));

        // writeFundCategoryMap 头调 1 次 deleteByUserAndFundName（单 fund）
        verify(fundCategoryMapMapper, times(1))
                .deleteByUserAndFundName(anyLong(), eq("长城短债债券A"));
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

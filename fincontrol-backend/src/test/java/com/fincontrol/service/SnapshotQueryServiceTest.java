package com.fincontrol.service;

import com.fincontrol.dto.category.CategoryMapMatchItem;
import com.fincontrol.dto.category.CategoryMapMatchResponse;
import com.fincontrol.dto.snapshot.SnapshotByDateResponse;
import com.fincontrol.dto.snapshot.SnapshotCategorySummary;
import com.fincontrol.dto.snapshot.SnapshotHistoryResponse;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.entity.AssetRaw;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetRawQueryMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import com.fincontrol.mapper.SnapshotMetaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 1a.4 + 1b.3 补救 SnapshotQueryService 业务测试。
 * <p>1b4pr6b-recovery（2026-07-25 03:30+）：新增 {@code latest_userCorrectAddsNewCategory_固收类}
 * 用例，覆盖 Fix D 后响应 categories 仍含「固收类」等 user_correct 引入的新类。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnapshotQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate SNAP_DATE = LocalDate.of(2026, 7, 16);

    @Mock private AssetSnapshotMapper assetSnapshotMapper;
    @Mock private AssetRawMapper assetRawMapper;
    @Mock private AssetRawQueryMapper assetRawQueryMapper;
    @Mock private SnapshotMetaMapper snapshotMetaMapper;
    @Mock private UserConfigService userConfigService;
    @Mock private CurrentSnapshotContext currentSnapshotContext;
    @Mock private CategoryMapService categoryMapService; // 1b4pr6b Fix D：构造需要

    @InjectMocks private SnapshotQueryService service;

    private AssetSnapshot snapOf(String category, BigDecimal total, BigDecimal target, BigDecimal actual) {
        AssetSnapshot snap = new AssetSnapshot();
        snap.setUserId(USER_ID);
        snap.setSnapshotDate(SNAP_DATE);
        snap.setCategory(category);
        snap.setTotalAmount(total);
        snap.setTargetRatio(target);
        snap.setActualRatio(actual);
        snap.setIsLatest(true);
        snap.setCreatedAt(LocalDateTime.of(2026, 7, 16, 10, 0));
        snap.setUpdatedAt(LocalDateTime.of(2026, 7, 16, 10, 0));
        return snap;
    }

    private AssetRaw rawOf(String fund, String category, BigDecimal amount) {
        AssetRaw row = new AssetRaw();
        row.setUserId(USER_ID);
        row.setSnapshotDate(SNAP_DATE);
        row.setFundName(fund);
        row.setCategory(category);
        row.setAmount(amount);
        row.setIsLatest(true);
        return row;
    }

    @BeforeEach
    void setUp() {
        when(currentSnapshotContext.resolveCurrentDate(USER_ID)).thenReturn(SNAP_DATE);
        when(userConfigService.loadSixCategoryTargetRatios(USER_ID)).thenReturn(Map.of(
                "货币类", new BigDecimal("10"),
                "固收类", new BigDecimal("15"),
                "商品类", new BigDecimal("25"),
                "A股权益类", new BigDecimal("25"),
                "海外权益类", new BigDecimal("20"),
                "港股大中华类", new BigDecimal("5")));
    }

    @Test
    @DisplayName("R3: 无 currentDate → 返回 null")
    void latest_noCurrentDate() {
        when(currentSnapshotContext.resolveCurrentDate(USER_ID)).thenReturn(null);
        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, true);
        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("R3/R4: snapshot 列表存在 → recompute sixTotal + target ratios")
    void latest_ratiosAreRecomputed() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("796.34"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("固收类", new BigDecimal("890.36"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("商品类", new BigDecimal("1642.86"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("A股权益类", new BigDecimal("2149.98"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("海外权益类", new BigDecimal("1675.98"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("港股大中华类", new BigDecimal("386.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("余额类", new BigDecimal("308.86"), BigDecimal.ZERO, BigDecimal.ZERO)));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(2);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, true);

        assertThat(resp).isNotNull();
        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("7541.52");
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("308.86");
        assertThat(resp.getTotalAssetWithBalance()).isEqualByComparingTo("7850.38");
        SnapshotCategorySummary commodity = resp.getCategories().stream()
                .filter(c -> "商品类".equals(c.getCategoryName())).findFirst().orElseThrow();
        assertThat(commodity.getActualRatio()).isEqualByComparingTo("21.78");
        assertThat(commodity.getTargetRatio()).isEqualByComparingTo("25");
        assertThat(commodity.getDeviation()).isEqualByComparingTo("-3.22");
        SnapshotCategorySummary balance = resp.getCategories().stream()
                .filter(c -> "余额类".equals(c.getCategoryName())).findFirst().orElseThrow();
        assertThat(balance.getActualRatio()).isNull();
        assertThat(balance.getTargetRatio()).isNull();
    }

    @Test
    @DisplayName("R4: 当前 snapshot 历史 0 比例时，权威 raw 重算")
    void latest_recomputesFromRawWhenSnapshotRatioZero() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("796.34"), BigDecimal.ZERO, BigDecimal.ZERO)));
        // sumSixCategoryAmountsAtDate 返回权威金额
        when(assetRawQueryMapper.sumSixCategoryAmountsAtDate(USER_ID, SNAP_DATE))
                .thenReturn(List.of(Map.of("category", "货币类", "total_amount", new BigDecimal("796.34"))));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(1);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, false);
        assertThat(resp).isNotNull();
        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("796.34");
    }

    @Test
    @DisplayName("R3: includeBalance=false 时余额类不进入 categories（6 canonical 齐出）")
    void latest_excludeBalance() {
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("796.34"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("余额类", new BigDecimal("308.86"), BigDecimal.ZERO, BigDecimal.ZERO)));
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(1);

        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, false);
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("0");
        // 1b4pr6b-recovery：即使 includeBalance=false，6 canonical 仍然齐出
        assertThat(resp.getCategories()).extracting("categoryName")
                .containsExactlyInAnyOrder(
                        "货币类", "固收类", "商品类", "A股权益类", "海外权益类", "港股大中华类");
    }

    // ============================================================
    // 1b4pr6b-recovery：Fix D 不完整导致固收类消失的回归用例
    // ============================================================

    /**
     * 模拟典型场景：AI 把「长城短债 A / 鹏华纯债 D / 安信新价值 A」误归为 A 股权益类，
     * 用户通过 fund_category_map 标 user_correct=固收类。
     * 期望 getLatest 返回的 categories 仍含「固收类」entry，total=1189.92，fundCount=3；
     * 总额含固收类后 6,433.22 → 7,623.14；总资产 7,764.08。
     */
    @Test
    @DisplayName("1b4pr6b-recovery: AI 误归 + user_correct → 响应补全固收类 summary")
    void latest_userCorrectAddsNewCategory_固收类() {
        // 1) AssetSnapshot 仅含 5 大类 + 余额类（没有固收类行）
        // 5 大类 中 A 股权益类 包括被 AI 误归的 3 只（人为原因以 user_correct 处理）
        // 未生效的总额 = 货币+商品+A股(含固收)+海外+港股+余额 = 6,433.22 + 140.94
        // 生效后的总额 = 货币+商品+A股(减固收 3)+海外+港股+固收+余额 = 7,623.14 + 140.94
        when(assetSnapshotMapper.selectLatestByUserAndDate(USER_ID, SNAP_DATE)).thenReturn(List.of(
                snapOf("货币类", new BigDecimal("796.52"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("商品类", new BigDecimal("1713.93"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("A股权益类", new BigDecimal("3072.42"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("海外权益类", new BigDecimal("1659.42"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("港股大中华类", new BigDecimal("380.85"), BigDecimal.ZERO, BigDecimal.ZERO),
                snapOf("余额类", new BigDecimal("140.94"), BigDecimal.ZERO, BigDecimal.ZERO)));

        // 2) 资产原始行：3 只被 AI 误归为 A 股权益类下"固收类"基金
        // 合计 = 454.72 + 435.90 + 299.30 = 1189.92
        Set<String> fundNames = new HashSet<>();
        fundNames.add("中加货币E");
        fundNames.add("华安黄金ETF联接C");
        fundNames.add("国泰黄金ETF联接C");
        fundNames.add("国泰黄金ETF联接A");
        fundNames.add("华安香港精选股票(QDII)");
        fundNames.add("易方达恒生科技ETF联接(QDII)C");
        fundNames.add("广发价值回报混合C");
        fundNames.add("诺安中证A100指数C");
        fundNames.add("易方达机器人ETF联接C");
        fundNames.add("国泰海通中证500指数增强C");
        fundNames.add("诺安中证A100指数A");
        fundNames.add("天弘纳斯达克100指数(QDII)C");
        fundNames.add("招商纳斯达克100ETF联接(QDII)C");
        fundNames.add("天弘纳斯达克100指数(QDII)A");
        fundNames.add("摩根纳斯达克100指数(QDII)A");
        fundNames.add("长城短债债券A");        // 未原 AI 误归，但含在集合中
        fundNames.add("鹏华纯债债券D");
        fundNames.add("安信新价值灵活配置混合A");
        fundNames.add("余额宝");
        when(assetRawMapper.selectFundNamesByUserAndDate(USER_ID, SNAP_DATE))
                .thenReturn(fundNames);

        // 3) asset_raw 行：按业务上设定的金额。5 大类 + 余额类下的金额。
        List<AssetRaw> rawRows = new ArrayList<>();
        // 被 AI 误归为 A 股权益类的"固收类"3 只
        rawRows.add(rawOf("长城短债债券A", "A股权益类", new BigDecimal("454.72")));
        rawRows.add(rawOf("鹏华纯债债券D", "A股权益类", new BigDecimal("435.90")));
        rawRows.add(rawOf("安信新价值灵活配置混合A", "A股权益类", new BigDecimal("299.30")));
        // 货币类
        rawRows.add(rawOf("中加货币E", "货币类", new BigDecimal("796.52")));
        // 商品类
        rawRows.add(rawOf("华安黄金ETF联接C", "商品类", new BigDecimal("157.32")));
        rawRows.add(rawOf("国泰黄金ETF联接C", "商品类", new BigDecimal("568.07")));
        rawRows.add(rawOf("国泰黄金ETF联接A", "商品类", new BigDecimal("988.54")));
        // 港股大中华类
        rawRows.add(rawOf("华安香港精选股票(QDII)", "港股大中华类", new BigDecimal("117.37")));
        rawRows.add(rawOf("易方达恒生科技ETF联接(QDII)C", "港股大中华类", new BigDecimal("263.48")));
        // A 股权益类 (只含 5 只 AI 正确归类的)
        rawRows.add(rawOf("广发价值回报混合C", "A股权益类", new BigDecimal("114.57")));
        rawRows.add(rawOf("诺安中证A100指数C", "A股权益类", new BigDecimal("103.93")));
        rawRows.add(rawOf("易方达机器人ETF联接C", "A股权益类", new BigDecimal("93.76")));
        rawRows.add(rawOf("国泰海通中证500指数增强C", "A股权益类", new BigDecimal("275.27")));
        rawRows.add(rawOf("诺安中证A100指数A", "A股权益类", new BigDecimal("1294.97")));
        // 海外权益类
        rawRows.add(rawOf("天弘纳斯达克100指数(QDII)C", "海外权益类", new BigDecimal("297.17")));
        rawRows.add(rawOf("招商纳斯达克100ETF联接(QDII)C", "海外权益类", new BigDecimal("177.74")));
        rawRows.add(rawOf("天弘纳斯达克100指数(QDII)A", "海外权益类", new BigDecimal("609.48")));
        rawRows.add(rawOf("摩根纳斯达克100指数(QDII)A", "海外权益类", new BigDecimal("575.03")));
        // 余额类
        rawRows.add(rawOf("余额宝", "余额类", new BigDecimal("140.94")));
        when(assetRawQueryMapper.selectCurrentByUserAndDate(USER_ID, SNAP_DATE))
                .thenReturn(rawRows);

        // 4) categoryMapService.match 返回 user_correct 覆盖
        List<CategoryMapMatchItem> matchItems = new ArrayList<>();
        matchItems.add(CategoryMapMatchItem.builder()
                .fundName("长城短债债券A").category("固收类")
                .source("user_correct").build());
        matchItems.add(CategoryMapMatchItem.builder()
                .fundName("鹏华纯债债券D").category("固收类")
                .source("user_correct").build());
        matchItems.add(CategoryMapMatchItem.builder()
                .fundName("安信新价值灵活配置混合A").category("固收类")
                .source("user_correct").build());
        when(categoryMapService.match(eq(USER_ID), any()))
                .thenReturn(CategoryMapMatchResponse.builder()
                        .matchedFunds(matchItems)
                        .unmatchedFunds(Collections.emptyList())
                        .build());

        // 5) no-override 路径的基金计数 mock（actual 有 override 时不走，但 lenient 需 stub）
        when(assetRawMapper.countFundsByUserAndDateAndCategory(eq(USER_ID), eq(SNAP_DATE), any()))
                .thenReturn(0); // override 路径下忽略

        // === 调用 ===
        SnapshotLatestResponse resp = service.getLatest(USER_ID, false, true);

        // === 断言 ===
        assertThat(resp).isNotNull();

        // 1. 总额含固收类 → 6,433.22 → 7,623.14；总资产 → 7,764.08
        // 6,433.22 + 1189.92 = 7,623.14
        // 7,623.14 + 140.94 = 7,764.08
        assertThat(resp.getSixCategoriesTotal()).isEqualByComparingTo("7623.14");
        assertThat(resp.getBalanceFund()).isEqualByComparingTo("140.94");
        assertThat(resp.getTotalAssetWithBalance()).isEqualByComparingTo("7764.08");

        // 2. categories 数组**始终含 7 大类**（6 类 + 余额类），不漏固收类
        assertThat(resp.getCategories())
                .extracting("categoryName")
                .containsExactlyInAnyOrder(
                        "货币类", "固收类", "商品类", "A股权益类", "海外权益类", "港股大中华类", "余额类");

        // 3. 固收类 entry 的 amount / fundCount 与 effective 集合一致
        SnapshotCategorySummary fixedIncome = resp.getCategories().stream()
                .filter(c -> "固收类".equals(c.getCategoryName()))
                .findFirst().orElseThrow();
        assertThat(fixedIncome.getCategoryTotal())
                .isEqualByComparingTo("1189.92");
        assertThat(fixedIncome.getFundCount()).isEqualTo(3);

        // 4. A 股权益类 已扣减 3 只基金的 1189.92
        SnapshotCategorySummary aShare = resp.getCategories().stream()
                .filter(c -> "A股权益类".equals(c.getCategoryName()))
                .findFirst().orElseThrow();
        assertThat(aShare.getCategoryTotal())
                .isEqualByComparingTo("1882.50"); // 3072.42 - 1189.92 = 1882.50

        // 5. 7 类都有 entry（防御性验证：不会被报空）
        assertThat(resp.getCategories()).hasSize(7);
    }
}

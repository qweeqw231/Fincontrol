package com.fincontrol.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1a.8.8 T-V3.2-01：CategoryEnum 7 canonical + 别名 + fromAlias 校验。
 *
 * <p>纯白盒测试，不依赖任何外部资源。
 */
class CategoryEnumTest {

    // ========================================================================
    // 7 canonical displayName
    // ========================================================================

    @Test
    @DisplayName("7 canonical displayName 全部命中 + ALL_DISPLAY_NAMES 长度 = 7")
    void sevenCanonicalNames() {
        assertThat(CategoryEnum.MONEY.getDisplayName()).isEqualTo("货币类");
        assertThat(CategoryEnum.FIXED_INCOME.getDisplayName()).isEqualTo("固收类");
        assertThat(CategoryEnum.COMMODITY.getDisplayName()).isEqualTo("商品类");
        assertThat(CategoryEnum.A_SHARE.getDisplayName()).isEqualTo("A股权益类");
        assertThat(CategoryEnum.OVERSEAS.getDisplayName()).isEqualTo("海外权益类");
        assertThat(CategoryEnum.HK_GREATER_CHINA.getDisplayName()).isEqualTo("港股大中华类");
        assertThat(CategoryEnum.BALANCE.getDisplayName()).isEqualTo("余额类");

        assertThat(CategoryEnum.ALL_DISPLAY_NAMES).hasSize(7);
        assertThat(CategoryEnum.SIX_CATEGORY_DISPLAY_NAMES).hasSize(6)
                .doesNotContain("余额类");
    }

    // ========================================================================
    // isValid: 7 canonical 命中，其他 null
    // ========================================================================

    @Test
    @DisplayName("isValid: 7 canonical 全部 true；null / 非法 / 空 → false")
    void isValid_canonical() {
        assertThat(CategoryEnum.isValid("货币类")).isTrue();
        assertThat(CategoryEnum.isValid("固收类")).isTrue();
        assertThat(CategoryEnum.isValid("商品类")).isTrue();
        assertThat(CategoryEnum.isValid("A股权益类")).isTrue();
        assertThat(CategoryEnum.isValid("海外权益类")).isTrue();
        assertThat(CategoryEnum.isValid("港股大中华类")).isTrue();
        assertThat(CategoryEnum.isValid("余额类")).isTrue();

        assertThat(CategoryEnum.isValid(null)).isFalse();
        assertThat(CategoryEnum.isValid("")).isFalse();
        assertThat(CategoryEnum.isValid("非法类")).isFalse();
        // 旧的"债券类/股票类/混合类" 已不在 7 canonical 内
        assertThat(CategoryEnum.isValid("债券类")).isFalse();
        assertThat(CategoryEnum.isValid("股票类")).isFalse();
        assertThat(CategoryEnum.isValid("混合类")).isFalse();
    }

    // ========================================================================
    // fromAlias: 命中 alias → canonical
    // ========================================================================

    @Test
    @DisplayName("fromAlias: 货币/货基/货币基金 → 货币类")
    void fromAlias_money() {
        assertThat(CategoryEnum.fromAlias("货币")).isEqualTo("货币类");
        assertThat(CategoryEnum.fromAlias("货基")).isEqualTo("货币类");
        assertThat(CategoryEnum.fromAlias("货币基金")).isEqualTo("货币类");
        assertThat(CategoryEnum.fromAlias("  货币  ")).isEqualTo("货币类");  // trim
    }

    @Test
    @DisplayName("fromAlias: QDII / 纳斯达克 / 海外股票 → 海外权益类")
    void fromAlias_overseas() {
        assertThat(CategoryEnum.fromAlias("QDII")).isEqualTo("海外权益类");
        assertThat(CategoryEnum.fromAlias("纳斯达克")).isEqualTo("海外权益类");
        assertThat(CategoryEnum.fromAlias("海外股票")).isEqualTo("海外权益类");
        assertThat(CategoryEnum.fromAlias("海外QDII")).isEqualTo("海外权益类");
    }

    @Test
    @DisplayName("fromAlias: 港股/大中华/恒生 → 港股大中华类（含旧别名「港股/大中华类」）")
    void fromAlias_hkGreaterChina() {
        assertThat(CategoryEnum.fromAlias("港股")).isEqualTo("港股大中华类");
        assertThat(CategoryEnum.fromAlias("大中华")).isEqualTo("港股大中华类");
        assertThat(CategoryEnum.fromAlias("恒生")).isEqualTo("港股大中华类");
        assertThat(CategoryEnum.fromAlias("港股QDII")).isEqualTo("港股大中华类");
        // 1a.8 旧别名兼容（fixture v3.1 用了）
        assertThat(CategoryEnum.fromAlias("港股/大中华类")).isEqualTo("港股大中华类");
        assertThat(CategoryEnum.fromAlias("港股/大中华")).isEqualTo("港股大中华类");
    }

    @Test
    @DisplayName("fromAlias: 纯债/短债/债券/固收 → 固收类（含旧「债券类」别名）")
    void fromAlias_fixedIncome() {
        assertThat(CategoryEnum.fromAlias("纯债")).isEqualTo("固收类");
        assertThat(CategoryEnum.fromAlias("短债")).isEqualTo("固收类");
        assertThat(CategoryEnum.fromAlias("债券")).isEqualTo("固收类");
        assertThat(CategoryEnum.fromAlias("固收")).isEqualTo("固收类");
        assertThat(CategoryEnum.fromAlias("债券类")).isEqualTo("固收类");  // 旧枚举兼容
    }

    @Test
    @DisplayName("fromAlias: A股/股票/股票类/中证 → A股权益类（含旧「股票类」别名）")
    void fromAlias_aShare() {
        assertThat(CategoryEnum.fromAlias("A股")).isEqualTo("A股权益类");
        assertThat(CategoryEnum.fromAlias("股票")).isEqualTo("A股权益类");
        assertThat(CategoryEnum.fromAlias("股票类")).isEqualTo("A股权益类");  // 旧枚举兼容
        assertThat(CategoryEnum.fromAlias("中证")).isEqualTo("A股权益类");
        assertThat(CategoryEnum.fromAlias("沪深300")).isEqualTo("A股权益类");
    }

    @Test
    @DisplayName("fromAlias: 商品/黄金/大宗商品 → 商品类")
    void fromAlias_commodity() {
        assertThat(CategoryEnum.fromAlias("商品")).isEqualTo("商品类");
        assertThat(CategoryEnum.fromAlias("黄金")).isEqualTo("商品类");
        assertThat(CategoryEnum.fromAlias("大宗商品")).isEqualTo("商品类");
    }

    @Test
    @DisplayName("fromAlias: 余额/余额宝 → 余额类")
    void fromAlias_balance() {
        assertThat(CategoryEnum.fromAlias("余额")).isEqualTo("余额类");
        assertThat(CategoryEnum.fromAlias("余额宝")).isEqualTo("余额类");
    }

    // ========================================================================
    // fromAlias: 未命中
    // ========================================================================

    @Test
    @DisplayName("fromAlias: null / 空 / 未知名 → null")
    void fromAlias_unknown() {
        assertThat(CategoryEnum.fromAlias(null)).isNull();
        assertThat(CategoryEnum.fromAlias("")).isNull();
        assertThat(CategoryEnum.fromAlias("   ")).isNull();
        assertThat(CategoryEnum.fromAlias("基金")).isNull();  // 旧「混合类」别名也未列入
        assertThat(CategoryEnum.fromAlias("完全未知")).isNull();
    }

    @Test
    @DisplayName("fromAlias: canonical 自身也能命中（identity 映射）")
    void fromAlias_canonicalSelf() {
        assertThat(CategoryEnum.fromAlias("货币类")).isEqualTo("货币类");
        assertThat(CategoryEnum.fromAlias("A股权益类")).isEqualTo("A股权益类");
        assertThat(CategoryEnum.fromAlias("余额类")).isEqualTo("余额类");
    }
}
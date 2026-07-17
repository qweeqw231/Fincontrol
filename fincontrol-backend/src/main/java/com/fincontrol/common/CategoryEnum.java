package com.fincontrol.common;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基金六大类枚举（[api-contract.md §2.2 / §7](#)）。
 *
 * <p>Phase 1 固定 6 大类：货币类 / 债券类 / 股票类 / 混合类 / 商品类 / 余额类。
 * <br>余额类不参与 6 大类 ratio 计算，但作为单独类别可被 1a.5 update 写入（[P0-3.3]）。
 *
 * <p>注意：既有用例（DedupEngine / SnapshotQueryService）仍以硬编码字符串 "余额类" 判定，
 * 本枚举仅供 1a.5 {@code CategoryMapService} 校验输入参数使用，不替代既有字符串常量。
 */
public enum CategoryEnum {
    MONEY("货币类"),
    BOND("债券类"),
    STOCK("股票类"),
    MIXED("混合类"),
    COMMODITY("商品类"),
    BALANCE("余额类");

    private final String displayName;

    CategoryEnum(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 六大类 displayName 集合（含余额类）。用于 Service 校验入参 category。 */
    public static final Set<String> ALL_DISPLAY_NAMES = Arrays.stream(values())
            .map(CategoryEnum::getDisplayName)
            .collect(Collectors.toUnmodifiableSet());

    /** 仅 6 大类（不含余额类）的 displayName 集合。 */
    public static final Set<String> SIX_CATEGORY_DISPLAY_NAMES = Arrays.stream(values())
            .filter(c -> c != BALANCE)
            .map(CategoryEnum::getDisplayName)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isValid(String displayName) {
        return displayName != null && ALL_DISPLAY_NAMES.contains(displayName);
    }
}
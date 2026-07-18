package com.fincontrol.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基金七大类枚举（[api-contract.md §2.2 / §7](#)，1a.8.8 决策 8）。
 *
 * <p>Phase 1 固定 7 大类：
 * <ul>
 *   <li>货币类</li>
 *   <li>固收类（原「债券类」收敛到此处，吸收纯债/短债/混合偏债 等别名）</li>
 *   <li>商品类</li>
 *   <li>A 股权益类（原「股票类」收敛到此处，吸收偏股混合/A 股指数 等别名）</li>
 *   <li>海外权益类（QDII / 纳斯达克 / 海外股票 全部归此类）</li>
 *   <li>港股大中华类（港股 / 恒生 / 大中华 / 港股 QDII）</li>
 *   <li>余额类（余额宝 + 余额；不参与 6 大类 ratio 计算；保留 [P0-3.3]）</li>
 * </ul>
 *
 * <p>每个 canonical 配一张 {@code aliases[]} 别名表，供 {@link #fromAlias(String)} 模糊匹配。
 * 1a.5 之前的「混合类」按持仓主属性分流入「固收类」/「A 股权益类」（1a.5 已淘汰此枚举值）。
 *
 * <p>既有用例（DedupEngine / SnapshotQueryService / AssetQueryService）仍以硬编码字符串 "余额类" 判定，
 * 本枚举的 {@link #BALANCE} 对应值一致，因此这些用例不受影响。
 */
public enum CategoryEnum {
    MONEY("货币类", new String[]{"货币", "货基", "货币基金"}),
    FIXED_INCOME("固收类", new String[]{"固收", "债券", "债券类", "纯债", "短债", "固收+", "中短债"}),
    COMMODITY("商品类", new String[]{"商品", "黄金", "大宗商品", "黄金ETF"}),
    A_SHARE("A股权益类", new String[]{"A股", "股票", "股票类", "A股权益", "股票指数", "中证", "宽基", "沪深300", "中证500", "中证1000"}),
    OVERSEAS("海外权益类", new String[]{"海外权益", "QDII", "海外股票", "海外QDII", "纳斯达克", "标普", "海外"}),
    HK_GREATER_CHINA("港股大中华类", new String[]{"港股", "大中华", "港股QDII", "恒生", "港股/大中华类", "港股/大中华"}),
    BALANCE("余额类", new String[]{"余额", "余额宝"});

    private final String displayName;
    private final String[] aliases;
    private final Set<String> aliasSet;

    CategoryEnum(String displayName, String[] aliases) {
        this.displayName = displayName;
        this.aliases = aliases;
        this.aliasSet = Collections.unmodifiableSet(
                Arrays.stream(aliases).collect(Collectors.toSet()));
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 该类别的别名集合（不可变）。 */
    public Set<String> getAliases() {
        return aliasSet;
    }

    /** 包含余额类的全部 canonical displayName 集合。 */
    public static final Set<String> ALL_DISPLAY_NAMES = Arrays.stream(values())
            .map(CategoryEnum::getDisplayName)
            .collect(Collectors.toUnmodifiableSet());

    /** 仅 6 大类（不含余额类）的 displayName 集合。 */
    public static final Set<String> SIX_CATEGORY_DISPLAY_NAMES = Arrays.stream(values())
            .filter(c -> c != BALANCE)
            .map(CategoryEnum::getDisplayName)
            .collect(Collectors.toUnmodifiableSet());

    /** 反向索引：alias → canonical displayName（首次匹配即返回）。 */
    private static final Map<String, String> ALIAS_TO_CANONICAL;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        for (CategoryEnum c : values()) {
            map.put(c.displayName, c.displayName);  // canonical 自身也算 alias
            for (String a : c.aliases) {
                map.putIfAbsent(a, c.displayName);
            }
        }
        ALIAS_TO_CANONICAL = Collections.unmodifiableMap(map);
    }

    /**
     * 把任意类别名（canonical 或 alias）规范化为 7 大类之一。
     *
     * @param name 输入字符串（已 trim、未判 null）
     * @return canonical displayName；未命中返回 null
     */
    public static String fromAlias(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        return ALIAS_TO_CANONICAL.get(trimmed);
    }

    /** 判断字符串是否为 7 canonical 之一。 */
    public static boolean isValid(String displayName) {
        return displayName != null && ALL_DISPLAY_NAMES.contains(displayName);
    }
}
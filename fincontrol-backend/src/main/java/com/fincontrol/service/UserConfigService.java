package com.fincontrol.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.CategoryEnum;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.mapper.UserConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 1b.3 补救：读取 user_config.target_ratios 并 canonicalize。
 * <p>不修改或回写历史快照；提供 fallback 默认值（10/15/25/25/20/5）。
 * <p>目标比例之和不为 100 时视为配置损坏并 fallback，避免静默用错。
 */
@Service
public class UserConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserConfigService.class);

    private static final Map<String, BigDecimal> DEFAULT_TARGET_RATIOS = new LinkedHashMap<>();
    static {
        DEFAULT_TARGET_RATIOS.put("货币类",     new BigDecimal("10"));
        DEFAULT_TARGET_RATIOS.put("固收类",     new BigDecimal("15"));
        DEFAULT_TARGET_RATIOS.put("商品类",     new BigDecimal("25"));
        DEFAULT_TARGET_RATIOS.put("A股权益类",  new BigDecimal("25"));
        DEFAULT_TARGET_RATIOS.put("海外权益类", new BigDecimal("20"));
        DEFAULT_TARGET_RATIOS.put("港股大中华类", new BigDecimal("5"));
    }

    private final UserConfigMapper userConfigMapper;
    private final ObjectMapper objectMapper;

    public UserConfigService(UserConfigMapper userConfigMapper, ObjectMapper objectMapper) {
        this.userConfigMapper = userConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取 user 的六大类目标比例；返回 canonical name → BigDecimal 映射。
     * <p>优先级：user_config.target_ratios JSON → 兜底默认值。
     * <p>六个 canonical 类别（不含余额类）总是返回；缺失/损坏/合计不为 100 时 fallback + 日志。
     */
    public Map<String, BigDecimal> loadSixCategoryTargetRatios(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 必填");
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : DEFAULT_TARGET_RATIOS.entrySet()) {
            result.put(e.getKey(), e.getValue());
        }
        String raw = safeRead(userId, "target_ratios");
        if (raw == null) {
            return result;
        }
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("1b.3 user_config.target_ratios 解析失败 userId={} raw={} -> fallback default; cause={}",
                    userId, raw, ex.toString());
            return result;
        }
        for (Map.Entry<String, Object> e : parsed.entrySet()) {
            String canonical = CategoryEnum.fromAlias(e.getKey());
            if (canonical == null || !"BALANCE".equals(getEnumName(canonical)) && !DEFAULT_TARGET_RATIOS.containsKey(canonical)) {
                // 不属于六大类或非余额类
                continue;
            }
            // 跳过余额类（如配置误写）
            if ("余额类".equals(canonical)) {
                continue;
            }
            BigDecimal v = toBigDecimal(e.getValue());
            if (v == null) continue;
            result.put(canonical, v);
        }
        BigDecimal sum = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(new BigDecimal("100")) != 0) {
            log.warn("1b.3 user_config.target_ratios 合计非 100% userId={} sum={} -> fallback default",
                    userId, sum.toPlainString());
            return new LinkedHashMap<>(DEFAULT_TARGET_RATIOS);
        }
        return result;
    }

    /** 兼容 category enum 名字：返回枚举的 displayName 用于与 DEFAULT_TARGET_RATIOS key 比较。 */
    private static String getEnumName(String canonical) {
        for (CategoryEnum c : CategoryEnum.values()) {
            if (c.getDisplayName().equals(canonical)) {
                return c.name();
            }
        }
        return canonical;
    }

    private String safeRead(Long userId, String key) {
        try {
            return userConfigMapper.selectValue(userId, key);
        } catch (Exception ex) {
            log.warn("1b.3 user_config 读取失败 userId={} key={} -> fallback; cause={}",
                    userId, key, ex.toString());
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(Objects.toString(v).trim());
        } catch (Exception ignore) {
            return null;
        }
    }
}

package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.entity.Settings;
import com.fincontrol.mapper.SettingsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * settings 全局配置服务（PR3plus 决策 31）。
 *
 * <p>第一个配置项：{@code max_snapshot_age_days}
 * <ul>
 *   <li>有效值：{-1, 7, 14, 30, 180}</li>
 *   <li>-1 = 不限制；其他 = 限定天数</li>
 * </ul>
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    /** 默认历史限制天数（找不到 settings 行时返回） */
    public static final int DEFAULT_MAX_SNAPSHOT_AGE_DAYS = 7;

    /** 有效值集合（含 -1） */
    private static final Set<Integer> VALID_DAYS = new HashSet<>(Arrays.asList(-1, 7, 14, 30, 180));

    private final SettingsMapper settingsMapper;

    public SettingsService(SettingsMapper settingsMapper) {
        this.settingsMapper = settingsMapper;
    }

    /**
     * 获取某用户的 max_snapshot_age_days。
     * 找不到 settings 行 → 返回默认 7。
     * @throws BusinessException 当 userId 为 null
     */
    public int getMaxSnapshotAgeDays(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 不能为空");
        }
        Settings s = settingsMapper.selectByUserId(userId);
        if (s == null || s.getMaxSnapshotAgeDays() == null) {
            log.debug("settings row not found for userId={}, fallback to default {}", userId, DEFAULT_MAX_SNAPSHOT_AGE_DAYS);
            return DEFAULT_MAX_SNAPSHOT_AGE_DAYS;
        }
        return s.getMaxSnapshotAgeDays();
    }

    /**
     * 设置某用户的 max_snapshot_age_days（upsert）。
     * @return 更新后的 Settings（含 updated_at）
     * @throws BusinessException 当 days 不在合法枚举内
     */
    public Settings setMaxSnapshotAgeDays(Long userId, int days) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId 不能为空");
        }
        if (!VALID_DAYS.contains(days)) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "max_snapshot_age_days 必须是 {-1, 7, 14, 30, 180} 之一，收到：" + days
            );
        }
        Settings s = new Settings();
        s.setUserId(userId);
        s.setMaxSnapshotAgeDays(days);
        int affected = settingsMapper.upsert(s);
        log.info("PR3plus settings updated: userId={} days={} affected={}", userId, days, affected);
        // 重新读一次拿最新的 updated_at
        return settingsMapper.selectByUserId(userId);
    }
}

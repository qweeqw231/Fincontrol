package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.entity.Settings;
import com.fincontrol.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * settings 全局配置 controller（PR3plus 决策 31）。
 *
 * <p>API：
 * <ul>
 *   <li>GET /api/settings/{userId}/max-snapshot-age-days → {maxSnapshotAgeDays}</li>
 *   <li>PUT /api/settings/{userId}/max-snapshot-age-days body:{days} → Settings</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/{userId}/max-snapshot-age-days")
    public ApiResponse<MaxAgeResponse> getMaxSnapshotAgeDays(@PathVariable Long userId) {
        int days = settingsService.getMaxSnapshotAgeDays(userId);
        log.info("PR3plus GET max-snapshot-age-days: userId={} days={}", userId, days);
        return ApiResponse.success(new MaxAgeResponse(days));
    }

    @PutMapping("/{userId}/max-snapshot-age-days")
    public ApiResponse<Settings> updateMaxSnapshotAgeDays(
            @PathVariable Long userId,
            @RequestBody UpdateMaxAgeRequest body
    ) {
        if (body == null) {
            return ApiResponse.error(1001, "请求体不能为空");
        }
        Settings s = settingsService.setMaxSnapshotAgeDays(userId, body.days());
        log.info("PR3plus PUT max-snapshot-age-days: userId={} newDays={}", userId, s.getMaxSnapshotAgeDays());
        return ApiResponse.success(s);
    }

    /** GET 响应体 */
    public record MaxAgeResponse(int maxSnapshotAgeDays) {}

    /** PUT 请求体 */
    public record UpdateMaxAgeRequest(Integer days) {}
}

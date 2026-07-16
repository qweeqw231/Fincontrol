package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.snapshot.SnapshotConfirmRequest;
import com.fincontrol.dto.snapshot.SnapshotConfirmResult;
import com.fincontrol.service.SnapShotConfirmService;
import com.fincontrol.service.SnapshotRollbackService;
import com.fincontrol.service.SnapshotRollbackService.RollbackResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 1a.3 confirm + 1a.8 rollback 端点（[docs/phase-1/designs/1a3-dedup-strategy.md §4.3](#)）。
 *
 * <p>POST /api/snapshot/confirm  — 1a.3 三表写入（@Transactional）
 * <br>DELETE /api/snapshot/confirm/{snapshotId}  — 1a.8 撤销（10s 窗口）
 *
 * <p>错误码由 {@code GlobalExceptionHandler} 统一映射：410 / 404 / 400 / 2001 等。
 */
@RestController
@RequestMapping("/api/snapshot")
public class SnapshotController {

    private static final Logger log = LoggerFactory.getLogger(SnapshotController.class);

    private final SnapShotConfirmService snapShotConfirmService;
    private final SnapshotRollbackService snapshotRollbackService;

    public SnapshotController(SnapShotConfirmService snapShotConfirmService,
                              SnapshotRollbackService snapshotRollbackService) {
        this.snapShotConfirmService = snapShotConfirmService;
        this.snapshotRollbackService = snapshotRollbackService;
    }

    // ========================================================================
    // POST /api/snapshot/confirm（1a.3）
    // ========================================================================

    /**
     * 1a.3 confirm 入口。
     * <p>Request body 见 {@link SnapshotConfirmRequest}（7 字段）。
     * <p>Response 200 — SnapShotConfirmService.confirm() 返回值。
     * <p>可能错误码：
     * <ul>
     *   <li>1001 — snapshotDate 与系统当前日相差 > 7 天</li>
     *   <li>400 — DedupEngine 跨 fund_name 跨 category 冲突</li>
     *   <li>2001 — 同日 snapshot 已有 + 未 confirmedOverwrite</li>
     *   <li>5001 — 写库或镜像校验失败</li>
     * </ul>
     */
    @PostMapping("/confirm")
    public ApiResponse<SnapshotConfirmResult> confirm(@RequestBody SnapshotConfirmRequest req) {
        log.info("1a.3 confirm: userId={} snapshotDate={} parsedAssetsCount={}",
                req.getUserId(), req.getSnapshotDate(),
                req.getParsedAssets() == null ? 0 : req.getParsedAssets().size());
        SnapshotConfirmResult result = snapShotConfirmService.confirm(req);
        return ApiResponse.success(result);
    }

    // ========================================================================
    // DELETE /api/snapshot/confirm/{snapshotId}（1a.8）
    // ========================================================================

    /**
     * 1a.8 撤销（10s 窗口内）。
     * <p>Path param {@code snapshotId}：要撤销的 asset_snapshot.id。
     * <p>Query param {@code userId}：用户 ID（防 CSRF / 跨用户撤销）。
     * <p>可能错误码：
     * <ul>
     *   <li>2001 — snapshot 不存在或不属于该 user</li>
     *   <li>2003 — 撤销窗口已过 10s → HTTP 410</li>
     * </ul>
     */
    @DeleteMapping("/confirm/{snapshotId}")
    public ApiResponse<RollbackResult> rollback(
            @PathVariable Long snapshotId,
            @RequestParam Long userId) {
        log.info("1a.8 rollback: snapshotId={} userId={}", snapshotId, userId);
        RollbackResult result = snapshotRollbackService.rollback(snapshotId, userId);
        return ApiResponse.success(result);
    }
}

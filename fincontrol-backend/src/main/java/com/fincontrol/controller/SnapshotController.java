package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.snapshot.SnapshotByDateResponse;
import com.fincontrol.dto.snapshot.SnapshotConfirmRequest;
import com.fincontrol.dto.snapshot.SnapshotConfirmResult;
import com.fincontrol.dto.snapshot.SnapshotHistoryResponse;
import com.fincontrol.dto.snapshot.SnapshotLatestResponse;
import com.fincontrol.dto.snapshot.SnapshotSetCurrentRequest;
import com.fincontrol.dto.snapshot.SnapshotSetCurrentResult;
import com.fincontrol.service.SnapshotMetaService;
import com.fincontrol.service.SnapShotConfirmService;
import com.fincontrol.service.SnapshotQueryService;
import com.fincontrol.service.SnapshotRollbackService;
import com.fincontrol.service.SnapshotRollbackService.RollbackResult;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final SnapshotQueryService snapshotQueryService;

    private final SnapshotMetaService snapshotMetaService; // 1b.3.3 决策 27

    public SnapshotController(SnapShotConfirmService snapShotConfirmService,
                              SnapshotRollbackService snapshotRollbackService,
                              SnapshotQueryService snapshotQueryService,
                              SnapshotMetaService snapshotMetaService) {
        this.snapShotConfirmService = snapShotConfirmService;
        this.snapshotRollbackService = snapshotRollbackService;
        this.snapshotQueryService = snapshotQueryService;
        this.snapshotMetaService = snapshotMetaService;
    }

    // ========================================================================
    // 1a.4 GET /api/snapshot/latest（latest / latest/detail）
    // ========================================================================

    /**
     * 1a.4 latest 汇总（[api-contract.md §3.1](#)）。
     * <p>无数据返回 code=0 + data=null，前端按空数据处理；不报 500。
     */
    @GetMapping("/latest")
    public ApiResponse<SnapshotLatestResponse> latest(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "true") boolean includeBalance,
            @RequestParam(defaultValue = "false") boolean includeDetail) {
        log.info("1a.4 latest: userId={} includeBalance={} includeDetail={}",
                userId, includeBalance, includeDetail);
        return ApiResponse.success(snapshotQueryService.getLatest(userId, includeDetail, includeBalance));
    }

    /**
     * 1a.4 latest 详情（[api-contract.md §3.2](#)）。强制 includeDetail=true。
     */
    @GetMapping("/latest/detail")
    public ApiResponse<SnapshotLatestResponse> latestDetail(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "true") boolean includeBalance) {
        log.info("1a.4 latest/detail: userId={} includeBalance={}", userId, includeBalance);
        return ApiResponse.success(snapshotQueryService.getLatest(userId, true, includeBalance));
    }

    // ========================================================================
    // 1a.4 Slice B：指定日期 + history
    // ========================================================================

    /**
     * 1a.4 指定日期快照（[api-contract.md §3.3](#)）。
     * <p>无数据由 Service 抛 2001，Controller 透传给 GlobalExceptionHandler。
     */
    @GetMapping("/{date}")
    public ApiResponse<SnapshotByDateResponse> byDate(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(defaultValue = "true") boolean includeBalance) {
        log.info("1a.4 byDate: userId={} date={} includeBalance={}", userId, date, includeBalance);
        return ApiResponse.success(snapshotQueryService.getByDate(userId, date, includeBalance));
    }

    /**
     * 1a.4 历史快照列表（[api-contract.md §3.4](#)）。
     */
    @GetMapping("/history")
    public ApiResponse<SnapshotHistoryResponse> history(
            @RequestHeader(name = "X-User-Id", defaultValue = "1") Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "true") boolean includeBalance) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "page 必须 >= 1");
        }
        log.info("1a.4 history: userId={} from={} to={} page={} pageSize={} includeBalance={}",
                userId, from, to, page, pageSize, includeBalance);
        return ApiResponse.success(snapshotQueryService.getHistory(
                userId, from, to, page, pageSize, includeBalance));
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

    // ========================================================================
    // 1b.3.3 决策 27: POST /api/snapshot/set-current
    // 切换 is_current=true 指向的 snapshot_date
    // ========================================================================

    /**
     * 1b.3.3 决策 27：手动切换当前快照。
     * <p>前置：snapshot_date 必须在 (user_id) 已有 is_latest=true 的行
     * <p>流程：clearCurrentForUser → setCurrent
     *
     * @param req 包含 userId + snapshotDate
     * @return previousCurrent + newCurrent + message
     */
    @PostMapping("/set-current")
    public ApiResponse<SnapshotSetCurrentResult> setCurrent(@RequestBody SnapshotSetCurrentRequest req) {
        log.info("1b.3.3 set-current: userId={} snapshotDate={}", req.getUserId(), req.getSnapshotDate());
        SnapshotSetCurrentResult result = snapshotMetaService.setCurrent(req.getUserId(), req.getSnapshotDate());
        return ApiResponse.success(result);
    }
}

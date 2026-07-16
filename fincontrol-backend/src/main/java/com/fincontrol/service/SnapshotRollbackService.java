package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 1a.8 快照撤销业务（[P0-3.2] 10s 撤销窗口 + 410 过期）。
 *
 * <p>两步软删除：把当前批 is_latest=false，恢复前版 is_latest=true。
 */
@Service
public class SnapshotRollbackService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRollbackService.class);

    /** 1a.8 撤销窗口（与 SnapShotConfirmService 保持一致） */
    private static final long ROLLBACK_WINDOW_SECONDS = 10L;

    private final AssetRawMapper assetRawMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;

    public SnapshotRollbackService(AssetRawMapper assetRawMapper,
                                  AssetSnapshotMapper assetSnapshotMapper) {
        this.assetRawMapper = assetRawMapper;
        this.assetSnapshotMapper = assetSnapshotMapper;
    }

    /**
     * 1a.8 撤销（10s 窗口内）。
     *
     * @return 结果 record（含旧版 snapshotDate 提示）
     * @throws BusinessException (2001 SNAPSHOT_NOT_FOUND | 2003 UNDO_TIMEOUT)
     */
    @Transactional
    public RollbackResult rollback(Long snapshotId, Long userId) {
        // 1. 读 snapshot
        AssetSnapshot snap = assetSnapshotMapper.selectById(snapshotId);
        if (snap == null || !snap.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND,
                    "snapshot id=" + snapshotId + " 不存在或不属于 user " + userId);
        }

        // 2. 验证 10s 窗口
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = snap.getCreatedAt().plusSeconds(ROLLBACK_WINDOW_SECONDS);
        if (now.isAfter(deadline)) {
            throw new BusinessException(ErrorCode.UNDO_TIMEOUT,
                    "snapshot id=" + snapshotId + " 撤销窗口已过 10s（created_at=" + snap.getCreatedAt() + "）");
        }

        LocalDate snapshotDate = snap.getSnapshotDate();

        // 3. 软删除当前批：把 user_id + snapshot_date 下所有 is_latest=true 行翻成 false
        int rawFlipped = assetRawMapper.updateIsLatestBySnapshotDate(userId, snapshotDate);
        int snapFlipped = assetSnapshotMapper.updateIsLatestBySnapshotDate(userId, snapshotDate);

        // 4. 恢复前版：找 user_id + date < current date 的最近 snapshot
        AssetSnapshot prev = assetSnapshotMapper.selectLatestBeforeDate(userId, snapshotDate);
        String prevSnapshotDate = null;
        if (prev != null) {
            assetRawMapper.updateIsLatestBySnapshotDate(userId, prev.getSnapshotDate());
            assetSnapshotMapper.updateIsLatestBySnapshotDate(userId, prev.getSnapshotDate());
            prevSnapshotDate = prev.getSnapshotDate().toString();
        }

        log.info("1a.8 rollback: snap={} user={} date={} rawFlipped={} snapFlipped={} prevDate={}",
                snapshotId, userId, snapshotDate, rawFlipped, snapFlipped, prevSnapshotDate);

        return new RollbackResult(
                true,
                snapshotDate.toString(),
                prevSnapshotDate,
                rawFlipped,
                snapFlipped
        );
    }

    /**
     * 撤销结果（Lombok @Data 自动生成 getter/setter）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackResult {
        private boolean rolledBack;
        private String snapshotDate;
        private String previousSnapshotRestored;
        private int assetRawUpdated;
        private int assetSnapshotUpdated;
    }
}

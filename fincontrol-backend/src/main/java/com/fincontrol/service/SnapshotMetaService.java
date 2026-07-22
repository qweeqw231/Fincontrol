package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.snapshot.SnapshotSetCurrentResult;
import com.fincontrol.entity.SnapshotMeta;
import com.fincontrol.mapper.SnapshotMetaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 1b.3.3 决策 27：snapshot_meta 业务编排。
 * <p>服务层用于：
 * <ul>
 *   <li>setCurrent(userId, snapshotDate) — 切换 is_current=true 指向</li>
 * </ul>
 * <p>写路径（confirm）已嵌入 SnapShotConfirmService.confirm()，不需单独 service 方法。
 */
@Service
public class SnapshotMetaService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotMetaService.class);

    private final SnapshotMetaMapper snapshotMetaMapper;

    public SnapshotMetaService(SnapshotMetaMapper snapshotMetaMapper) {
        this.snapshotMetaMapper = snapshotMetaMapper;
    }

    /**
     * 1b.3.3：切换当前快照。
     * <p>前置：snapshot_date 必须在 (user_id) 已有 is_latest=true 的行
     * <p>事务：clearCurrentForUser + setCurrent（原子操作）
     *
     * @param userId      用户 ID
     * @param snapshotDate 目标 snapshot_date
     * @return 前 / 后 current 快照
     */
    @Transactional
    public SnapshotSetCurrentResult setCurrent(Long userId, LocalDate snapshotDate) {
        if (userId == null || snapshotDate == null) {
            throw new BusinessException(ErrorCode.INVALID_SNAPSHOT_DATE,
                    "userId / snapshotDate 必填");
        }

        // 校验：snapshot_date 必须存在 is_latest=true 的行
        SnapshotMeta existed = snapshotMetaMapper.selectByUserAndDate(userId, snapshotDate);
        if (existed == null || !Boolean.TRUE.equals(existed.getIsLatest())) {
            throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND,
                    "snapshot_date " + snapshotDate + " 暂无 is_latest 快照，请先 confirm");
        }

        // 记录 previous current
        LocalDate previousCurrent = snapshotMetaMapper.selectCurrentDateByUser(userId);

        // Step 1：翻 (user_id) 全部 is_current=false
        snapshotMetaMapper.clearCurrentForUser(userId);

        // Step 2：翻 (user_id, snapshot_date) is_current=true
        int updated = snapshotMetaMapper.setCurrent(userId, snapshotDate);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "setCurrent 失败，影响行数 0");
        }

        log.info("1b.3.3 set-current: userId={} previous={} new={}",
                userId, previousCurrent, snapshotDate);

        return SnapshotSetCurrentResult.builder()
                .userId(userId)
                .previousCurrent(previousCurrent)
                .newCurrent(snapshotDate)
                .message("快照切换成功")
                .build();
        /**
     * 1b.3.9 决策 27：列 (user_id) 所有 snapshot_meta 行（按日期 desc）。
     */
    public List<SnapshotMeta> listByUser(Long userId) {
        return snapshotMetaMapper.selectAllByUser(userId);
    }
}
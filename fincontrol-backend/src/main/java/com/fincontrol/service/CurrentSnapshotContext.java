package com.fincontrol.service;

import com.fincontrol.mapper.SnapshotMetaMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 1b.3 补救 R3：统一当前快照日期查询入口。
 * <p>所有首页辅助接口在读取具体行/聚合之前，必须先经过本类获取 currentDate。
 * <p>这样余额、收益、基金数、占比全部绑定到同一日期，避免跨日累计。
 */
@Service
public class CurrentSnapshotContext {

    private final SnapshotMetaMapper snapshotMetaMapper;

    public CurrentSnapshotContext(SnapshotMetaMapper snapshotMetaMapper) {
        this.snapshotMetaMapper = snapshotMetaMapper;
    }

    /**
     * 查找 user 的当前快照日期（snapshot_meta.is_current=true）。
     * <p>无任何快照时返回 null，调用方应展示 Empty 状态。
     */
    public LocalDate resolveCurrentDate(Long userId) {
        if (userId == null) return null;
        return snapshotMetaMapper.selectCurrentDateByUser(userId);
    }
}

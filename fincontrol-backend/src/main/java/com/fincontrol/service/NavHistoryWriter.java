package com.fincontrol.service;

import com.fincontrol.entity.NavHistory;
import com.fincontrol.mapper.NavHistoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 2b：nav_history 批量幂等写入（独立 bean 以便 {@link Transactional} 代理生效）。
 *
 * <p>拆出为单独类的原因：{@code NavSyncService} 的定时方法/手动方法内部调用自身方法时
 * 会绕过事务代理；跨 bean 调用才能保证「全量解析结果在同一事务内 upsert」。
 */
@Service
public class NavHistoryWriter {

    private final NavHistoryMapper navHistoryMapper;

    public NavHistoryWriter(NavHistoryMapper navHistoryMapper) {
        this.navHistoryMapper = navHistoryMapper;
    }

    /** 按 (user_id, nav_date) 幂等 upsert 全部行，返回写入行数。 */
    @Transactional
    public int upsertAll(List<NavHistory> rows) {
        int count = 0;
        for (NavHistory row : rows) {
            navHistoryMapper.upsert(row);
            count++;
        }
        return count;
    }
}
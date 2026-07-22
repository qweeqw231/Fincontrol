package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.dto.snapshot.SnapshotSetCurrentResult;
import com.fincontrol.entity.SnapshotMeta;
import com.fincontrol.mapper.SnapshotMetaMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1b.3.5 决策 27：SnapshotMetaService 单测。
 * <p>覆盖 setCurrent(userId, snapshotDate) 边界：
 * <ul>
 *   <li>正常切换：previousCurrent + newCurrent + clearCurrentForUser + setCurrent 调用</li>
 *   <li>空 userId / snapshotDate 抛 INVALID_SNAPSHOT_DATE</li>
 *   <li>snapshot_date 不存在 is_latest 抛 SNAPSHOT_NOT_FOUND</li>
 *   <li>setCurrent 影响行数 0 抛 INTERNAL_ERROR</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SnapshotMetaServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 16);
    private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 7, 20);

    @Mock
    private SnapshotMetaMapper snapshotMetaMapper;

    @InjectMocks
    private SnapshotMetaService snapshotMetaService;

    @Test
    @DisplayName("setCurrent 正常切换：返回 previousCurrent + newCurrent + 调用 clearCurrentForUser + setCurrent")
    void setCurrent_success() {
        // Arrange
        SnapshotMeta existed = new SnapshotMeta();
        existed.setUserId(USER_ID);
        existed.setSnapshotDate(TARGET_DATE);
        existed.setIsLatest(true);
        existed.setIsCurrent(false);
        when(snapshotMetaMapper.selectByUserAndDate(USER_ID, TARGET_DATE)).thenReturn(existed);
        when(snapshotMetaMapper.selectCurrentDateByUser(USER_ID)).thenReturn(PREVIOUS_DATE);
        when(snapshotMetaMapper.setCurrent(USER_ID, TARGET_DATE)).thenReturn(1);

        // Act
        SnapshotSetCurrentResult result = snapshotMetaService.setCurrent(USER_ID, TARGET_DATE);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getPreviousCurrent()).isEqualTo(PREVIOUS_DATE);
        assertThat(result.getNewCurrent()).isEqualTo(TARGET_DATE);
        assertThat(result.getMessage()).isEqualTo("快照切换成功");

        verify(snapshotMetaMapper).clearCurrentForUser(USER_ID);
        verify(snapshotMetaMapper).setCurrent(USER_ID, TARGET_DATE);
    }

    @Test
    @DisplayName("setCurrent 空 userId 抛 INVALID_SNAPSHOT_DATE")
    void setCurrent_nullUserId() {
        assertThatThrownBy(() -> snapshotMetaService.setCurrent(null, TARGET_DATE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("setCurrent 空 snapshotDate 抛 INVALID_SNAPSHOT_DATE")
    void setCurrent_nullSnapshotDate() {
        assertThatThrownBy(() -> snapshotMetaService.setCurrent(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("snapshotDate");
    }

    @Test
    @DisplayName("setCurrent snapshot_date 不存在抛 SNAPSHOT_NOT_FOUND")
    void setCurrent_notExisted() {
        when(snapshotMetaMapper.selectByUserAndDate(USER_ID, TARGET_DATE)).thenReturn(null);

        assertThatThrownBy(() -> snapshotMetaService.setCurrent(USER_ID, TARGET_DATE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无 is_latest 快照");

        verify(snapshotMetaMapper, never()).clearCurrentForUser(any());
        verify(snapshotMetaMapper, never()).setCurrent(any(), any());
    }

    @Test
    @DisplayName("setCurrent snapshot_date 存在但 is_latest=false 抛 SNAPSHOT_NOT_FOUND")
    void setCurrent_notLatest() {
        SnapshotMeta existed = new SnapshotMeta();
        existed.setUserId(USER_ID);
        existed.setSnapshotDate(TARGET_DATE);
        existed.setIsLatest(false);  // 不是 latest
        existed.setIsCurrent(false);
        when(snapshotMetaMapper.selectByUserAndDate(USER_ID, TARGET_DATE)).thenReturn(existed);

        assertThatThrownBy(() -> snapshotMetaService.setCurrent(USER_ID, TARGET_DATE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无 is_latest 快照");

        verify(snapshotMetaMapper, never()).clearCurrentForUser(any());
    }

    @Test
    @DisplayName("setCurrent 影响行数 0 抛 INTERNAL_ERROR")
    void setCurrent_zeroRows() {
        SnapshotMeta existed = new SnapshotMeta();
        existed.setIsLatest(true);
        when(snapshotMetaMapper.selectByUserAndDate(USER_ID, TARGET_DATE)).thenReturn(existed);
        when(snapshotMetaMapper.selectCurrentDateByUser(USER_ID)).thenReturn(PREVIOUS_DATE);
        when(snapshotMetaMapper.setCurrent(USER_ID, TARGET_DATE)).thenReturn(0);

        assertThatThrownBy(() -> snapshotMetaService.setCurrent(USER_ID, TARGET_DATE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("setCurrent 失败");
    }

    @Test
    @DisplayName("setCurrent 首次切换 previousCurrent 为 null")
    void setCurrent_firstTime() {
        SnapshotMeta existed = new SnapshotMeta();
        existed.setIsLatest(true);
        when(snapshotMetaMapper.selectByUserAndDate(USER_ID, TARGET_DATE)).thenReturn(existed);
        when(snapshotMetaMapper.selectCurrentDateByUser(USER_ID)).thenReturn(null);
        when(snapshotMetaMapper.setCurrent(USER_ID, TARGET_DATE)).thenReturn(1);

        SnapshotSetCurrentResult result = snapshotMetaService.setCurrent(USER_ID, TARGET_DATE);

        assertThat(result.getPreviousCurrent()).isNull();
        assertThat(result.getNewCurrent()).isEqualTo(TARGET_DATE);
    }
}

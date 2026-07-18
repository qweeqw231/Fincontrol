package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.entity.AssetSnapshot;
import com.fincontrol.mapper.AssetRawMapper;
import com.fincontrol.mapper.AssetSnapshotMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 1a.7 补测：SnapshotRollbackService 业务测试（[P0-3.2] 10s 撤销 + 410 过期）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>A7-T01 10s 内成功翻转 is_latest</li>
 *   <li>A7-T02 snapshot 不存在 → SNAPSHOT_NOT_FOUND</li>
 *   <li>A7-T03 snapshot 属于其他 user → SNAPSHOT_NOT_FOUND（隔离）</li>
 *   <li>A7-T04 撤销窗口已过 10s → UNDO_TIMEOUT</li>
 *   <li>A7-T05 撤销后无前版 snapshot（首次入库）→ 不调恢复 mapper</li>
 *   <li>A7-T06 撤销后存在前版 → 调用恢复 mapper 2 次</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RollbackServiceTest {

    @Mock private AssetRawMapper assetRawMapper;
    @Mock private AssetSnapshotMapper assetSnapshotMapper;

    @InjectMocks private SnapshotRollbackService service;

    private static final Long USER_ID = 1L;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 17);

    private AssetSnapshot newSnapshot(LocalDateTime createdAt) {
        AssetSnapshot snap = new AssetSnapshot();
        snap.setId(100L);
        snap.setUserId(USER_ID);
        snap.setSnapshotDate(TEST_DATE);
        snap.setCategory("权益类");
        snap.setTotalAmount(new BigDecimal("100.00"));
        snap.setIsLatest(true);
        snap.setCreatedAt(createdAt);
        return snap;
    }

    @Test
    @DisplayName("A7-T01: 10s 内撤销 → is_latest 翻转 + prev 恢复")
    void rollback_within10s_flipsAndRestoresPrev() {
        when(assetSnapshotMapper.selectById(100L)).thenReturn(newSnapshot(LocalDateTime.now().minusSeconds(3)));
        when(assetRawMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(3);
        when(assetSnapshotMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(2);
        AssetSnapshot prev = newSnapshot(TEST_DATE.minusDays(1).atStartOfDay());
        prev.setId(99L);
        prev.setSnapshotDate(TEST_DATE.minusDays(1));
        when(assetSnapshotMapper.selectLatestBeforeDate(USER_ID, TEST_DATE)).thenReturn(prev);

        SnapshotRollbackService.RollbackResult result = service.rollback(100L, USER_ID);

        assertThat(result.isRolledBack()).isTrue();
        assertThat(result.getSnapshotDate()).isEqualTo(TEST_DATE.toString());
        assertThat(result.getPreviousSnapshotRestored()).isEqualTo(TEST_DATE.minusDays(1).toString());
        assertThat(result.getAssetRawUpdated()).isEqualTo(3);
        assertThat(result.getAssetSnapshotUpdated()).isEqualTo(2);
        // 恢复前版：再调用 2 次 mapper（raw + snapshot）
        verify(assetRawMapper).updateIsLatestBySnapshotDate(USER_ID, TEST_DATE.minusDays(1));
        verify(assetSnapshotMapper).updateIsLatestBySnapshotDate(USER_ID, TEST_DATE.minusDays(1));
    }

    @Test
    @DisplayName("A7-T02: snapshotId 不存在 → SNAPSHOT_NOT_FOUND")
    void rollback_snapshotNotFound_throws() {
        when(assetSnapshotMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.rollback(999L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.SNAPSHOT_NOT_FOUND);
                    assertThat(be.getMessage()).contains("999");
                });
        // 不调任何 mapper
        verify(assetRawMapper, never()).updateIsLatestBySnapshotDate(anyLong(), any());
        verify(assetSnapshotMapper, never()).updateIsLatestBySnapshotDate(anyLong(), any());
    }

    @Test
    @DisplayName("A7-T03: snapshot 属于其他 user → SNAPSHOT_NOT_FOUND（user 隔离）")
    void rollback_snapshotBelongsToOtherUser_throws() {
        AssetSnapshot otherUsers = newSnapshot(LocalDateTime.now());
        otherUsers.setUserId(999L);  // 不是 USER_ID
        when(assetSnapshotMapper.selectById(100L)).thenReturn(otherUsers);

        assertThatThrownBy(() -> service.rollback(100L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.SNAPSHOT_NOT_FOUND.getCode());

        verify(assetRawMapper, never()).updateIsLatestBySnapshotDate(anyLong(), any());
    }

    @Test
    @DisplayName("A7-T04: 撤销窗口已过 10s → UNDO_TIMEOUT")
    void rollback_after10sWindow_throwsUndoTimeout() {
        // created_at 早于当前 30s
        when(assetSnapshotMapper.selectById(100L)).thenReturn(newSnapshot(LocalDateTime.now().minusSeconds(30)));

        assertThatThrownBy(() -> service.rollback(100L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code").isEqualTo(ErrorCode.UNDO_TIMEOUT.getCode());
        // 不调翻转 mapper
        verify(assetRawMapper, never()).updateIsLatestBySnapshotDate(anyLong(), any());
    }

    @Test
    @DisplayName("A7-T05: 首次入库（无前版）→ 不调恢复 mapper")
    void rollback_noPrevSnapshot_doesNotRestore() {
        when(assetSnapshotMapper.selectById(100L)).thenReturn(newSnapshot(LocalDateTime.now().minusSeconds(2)));
        when(assetRawMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(2);
        when(assetSnapshotMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(1);
        when(assetSnapshotMapper.selectLatestBeforeDate(USER_ID, TEST_DATE)).thenReturn(null);

        SnapshotRollbackService.RollbackResult result = service.rollback(100L, USER_ID);

        assertThat(result.isRolledBack()).isTrue();
        assertThat(result.getPreviousSnapshotRestored()).isNull();
        // 不调恢复 mapper（只调了"翻转"2 次 + "查前版"1 次，共 3 次 mapper 调用）
        verify(assetRawMapper, never()).updateIsLatestBySnapshotDate(USER_ID, TEST_DATE.minusDays(1));
    }

    @Test
    @DisplayName("A7-T06: 翻转数为 0 时仍返 RollbackResult（业务允空翻）")
    void rollback_zeroFlippedCount_stillSuccess() {
        when(assetSnapshotMapper.selectById(100L)).thenReturn(newSnapshot(LocalDateTime.now().minusSeconds(1)));
        when(assetRawMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(0);
        when(assetSnapshotMapper.updateIsLatestBySnapshotDate(USER_ID, TEST_DATE)).thenReturn(0);
        when(assetSnapshotMapper.selectLatestBeforeDate(USER_ID, TEST_DATE)).thenReturn(null);

        SnapshotRollbackService.RollbackResult result = service.rollback(100L, USER_ID);

        assertThat(result.isRolledBack()).isTrue();
        assertThat(result.getAssetRawUpdated()).isEqualTo(0);
        assertThat(result.getAssetSnapshotUpdated()).isEqualTo(0);
    }
}
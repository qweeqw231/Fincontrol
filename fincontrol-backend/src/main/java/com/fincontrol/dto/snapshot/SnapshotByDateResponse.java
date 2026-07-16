package com.fincontrol.dto.snapshot;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 1a.4 `GET /api/snapshot/{date}` 响应体（[api-contract.md §3.3](#)）。
 *
 * <p>复用 {@link SnapshotLatestResponse} 的字段结构；该日期无数据时上层抛
 * {@link BusinessException} {@code 2001}，不返回空集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotByDateResponse {

    private LocalDate snapshotDate;

    private LocalDateTime snapshotConfirmedAt;

    private BigDecimal sixCategoriesTotal;

    private BigDecimal balanceFund;

    private BigDecimal totalAssetWithBalance;

    private List<SnapshotCategorySummary> categories;

    /** Service 找不到时直接抛 {@link ErrorCode#SNAPSHOT_NOT_FOUND}；Controller 不写 null data。 */
    public static SnapshotByDateResponse notFound(LocalDate date) {
        throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND,
                "该日期无快照数据: " + date);
    }
}

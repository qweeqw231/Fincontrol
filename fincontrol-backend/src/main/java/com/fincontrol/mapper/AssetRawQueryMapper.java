package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.AssetRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 1b.3 补救 R3：限定到当前快照日期的统计与查询。
 * <p>既有 {@code AssetRawMapper} 中部分查询使用 {@code is_latest=1} 或 {@code MAX(snapshot_date)}，
 * 在决策 27 引入 {@code snapshot_meta.is_current} 之后，首页各数据源
 * 必须与 {@code is_current} 关联的日期严格一致，因此补充一份“按 currentDate 限定”的查询。
 */
@Mapper
public interface AssetRawQueryMapper extends BaseMapper<AssetRaw> {

    /**
     * 限定到给定日期的资产原始行（is_latest=1 AND snapshot_date = currentDate）。
     */
    @Select("SELECT * FROM asset_raw " +
            "WHERE user_id = #{userId} AND is_latest = 1 " +
            "AND snapshot_date = #{snapshotDate} " +
            "ORDER BY category, id")
    List<AssetRaw> selectCurrentByUserAndDate(@Param("userId") Long userId,
                                              @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 限定到给定日期的余额类行（用于 AssetQueryService 余额 fallback 读取）。
     */
    @Select("SELECT * FROM asset_raw " +
            "WHERE user_id = #{userId} AND is_latest = 1 " +
            "AND snapshot_date = #{snapshotDate} AND category = '余额类'")
    List<AssetRaw> selectCurrentBalanceByUser(@Param("userId") Long userId,
                                             @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 限定到给定日期的累计/持有收益五字段（双保险：snapshot_date 与 is_latest 共同约束）。
     */
    @Select("SELECT COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit, " +
            "COALESCE(SUM(holding_profit), 0) AS total_holding_profit, " +
            "COALESCE(SUM(amount), 0) AS total_amount, " +
            "COUNT(*) AS fund_count, " +
            "MAX(snapshot_date) AS snapshot_date " +
            "FROM asset_raw " +
            "WHERE user_id = #{userId} AND is_latest = 1 " +
            "AND snapshot_date = #{snapshotDate}")
    Map<String, Object> sumReturnFieldsAtDate(@Param("userId") Long userId,
                                             @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 统计给定日期下收益字段的覆盖情况（用于判定该快照是否含逐基金收益数据）。
     * <p>外部 Excel 导入路径（asset_table_total 等）只有市值/占比列，会把
     * holding_profit / cumulative_profit 写为 NULL；此时前端卡片不能显示 +0.00（会误导为"零收益"），
     * 而应降级为净值历史的组合级累加值或显示"不可用"。
     */
    @Select("SELECT COUNT(*) AS total_cnt, " +
            "COALESCE(SUM(cumulative_profit IS NOT NULL), 0) AS cum_cnt, " +
            "COALESCE(SUM(holding_profit IS NOT NULL), 0) AS hold_cnt " +
            "FROM asset_raw " +
            "WHERE user_id = #{userId} AND is_latest = 1 " +
            "AND snapshot_date = #{snapshotDate}")
    Map<String, Object> countProfitFieldsAtDate(@Param("userId") Long userId,
                                                @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 限定到给定日期的六大类金额合计（用于 R4 实际比例重算）。
     */
    @Select("SELECT category, COALESCE(SUM(amount), 0) AS total_amount, COUNT(*) AS fund_count " +
            "FROM asset_raw " +
            "WHERE user_id = #{userId} AND is_latest = 1 " +
            "AND snapshot_date = #{snapshotDate} AND category <> '余额类' " +
            "GROUP BY category " +
            "ORDER BY category")
    List<Map<String, Object>> sumSixCategoryAmountsAtDate(@Param("userId") Long userId,
                                                         @Param("snapshotDate") LocalDate snapshotDate);
}

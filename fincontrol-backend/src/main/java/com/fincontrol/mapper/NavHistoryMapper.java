package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.NavHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * nav_history 表 DAO（Phase 3 可视化）。
 */
@Mapper
public interface NavHistoryMapper extends BaseMapper<NavHistory> {

    @Select("SELECT * FROM nav_history WHERE user_id = #{userId} ORDER BY nav_date ASC")
    List<NavHistory> selectAllByUser(@Param("userId") Long userId);

    /**
     * 取给定日期（含）之前最近一条净值记录。
     * <p>用于首页「累计收益」的降级取值：当快照来源（如外部 Excel 导入）不含逐基金收益字段时，
     * 用同账户的组合级累计盈亏（累加口径）替代，避免卡片显示误导性的 +0.00。
     */
    @Select("SELECT * FROM nav_history WHERE user_id = #{userId} AND nav_date <= #{date} " +
            "ORDER BY nav_date DESC LIMIT 1")
    NavHistory selectLatestOnOrBefore(@Param("userId") Long userId,
                                      @Param("date") java.time.LocalDate date);
}

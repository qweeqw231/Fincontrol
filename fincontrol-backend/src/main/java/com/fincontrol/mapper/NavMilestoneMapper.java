package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.NavMilestone;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * nav_milestone 表 DAO（Phase 3 可视化标注）。
 */
@Mapper
public interface NavMilestoneMapper extends BaseMapper<NavMilestone> {

    @Select("SELECT * FROM nav_milestone WHERE user_id = #{userId} ORDER BY sort_order ASC")
    List<NavMilestone> selectAllByUser(@Param("userId") Long userId);
}

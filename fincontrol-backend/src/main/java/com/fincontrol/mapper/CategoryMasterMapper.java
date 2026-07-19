package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.CategoryMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 1a.10 category_master DAO。 */
@Mapper
public interface CategoryMasterMapper extends BaseMapper<CategoryMaster> {

    @Select("SELECT * FROM category_master WHERE is_active = TRUE ORDER BY id")
    List<CategoryMaster> selectAllActive();

    @Select("SELECT * FROM category_master ORDER BY id")
    List<CategoryMaster> selectAllIncludingInactive();

    @Select("SELECT * FROM category_master WHERE name_canonical = #{name} LIMIT 1")
    CategoryMaster selectByCanonical(@Param("name") String name);

    @Update("UPDATE category_master SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int deactivateById(@Param("id") Long id);
}

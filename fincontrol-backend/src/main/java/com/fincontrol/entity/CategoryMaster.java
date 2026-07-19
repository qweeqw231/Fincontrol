package com.fincontrol.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 1a.10 七大类主数据；aliases 使用 JSON 数组字符串存储。 */
@Data
@NoArgsConstructor
@TableName("category_master")
public class CategoryMaster {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name_canonical")
    private String nameCanonical;

    @TableField("aliases")
    private String aliases;

    @TableField("is_active")
    private Boolean active;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

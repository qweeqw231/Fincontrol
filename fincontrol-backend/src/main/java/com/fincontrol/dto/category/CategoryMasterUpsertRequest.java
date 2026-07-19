package com.fincontrol.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** category_master 新建/完整更新请求。 */
@Data
public class CategoryMasterUpsertRequest {

    @NotBlank(message = "nameCanonical 必填")
    @Size(max = 50, message = "nameCanonical 最长 50 字符")
    private String nameCanonical;

    @NotNull(message = "aliases 必填")
    @Size(max = 100, message = "aliases 最多 100 个")
    private List<@NotBlank(message = "alias 不能为空") String> aliases;

    private Boolean active = true;
}

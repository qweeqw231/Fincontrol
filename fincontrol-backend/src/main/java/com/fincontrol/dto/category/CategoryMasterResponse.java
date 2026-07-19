package com.fincontrol.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** category_master 对外响应，aliases 已反序列化为数组。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMasterResponse {
    private Long id;
    private String nameCanonical;
    private List<String> aliases;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

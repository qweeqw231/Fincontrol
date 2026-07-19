package com.fincontrol.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.common.BusinessException;
import com.fincontrol.common.CategoryEnum;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.category.CategoryMasterResponse;
import com.fincontrol.dto.category.CategoryMasterUpsertRequest;
import com.fincontrol.entity.CategoryMaster;
import com.fincontrol.mapper.CategoryMasterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 1a.10 七大类主数据 CRUD 与 DB-driven alias 索引。 */
@Service
public class CategoryMasterService {

    private static final Logger log = LoggerFactory.getLogger(CategoryMasterService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final CategoryMasterMapper mapper;
    private final ObjectMapper objectMapper;
    private volatile Map<String, String> activeAliasIndex;

    public CategoryMasterService(CategoryMasterMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public List<CategoryMasterResponse> list(boolean includeInactive) {
        List<CategoryMaster> rows = includeInactive
                ? mapper.selectAllIncludingInactive()
                : mapper.selectAllActive();
        if (rows == null) return List.of();
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryMasterResponse create(CategoryMasterUpsertRequest request) {
        NormalizedInput input = normalize(request);
        validateNoConflict(null, input);

        CategoryMaster row = new CategoryMaster();
        row.setNameCanonical(input.canonical());
        row.setAliases(writeAliases(input.aliases()));
        row.setActive(input.active());
        if (mapper.insert(row) != 1) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "category_master INSERT 失败");
        }
        invalidateCache();
        return toResponse(requireRow(row.getId()));
    }

    @Transactional
    public CategoryMasterResponse update(Long id, CategoryMasterUpsertRequest request) {
        if (id == null) throw invalid("id 必填");
        CategoryMaster existing = mapper.selectById(id);
        if (existing == null) throw invalid("category_master id=" + id + " 不存在");

        NormalizedInput input = normalize(request);
        validateNoConflict(id, input);
        existing.setNameCanonical(input.canonical());
        existing.setAliases(writeAliases(input.aliases()));
        existing.setActive(input.active());
        if (mapper.updateById(existing) != 1) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR,
                    "category_master UPDATE 失败: id=" + id);
        }
        invalidateCache();
        return toResponse(requireRow(id));
    }

    /** 软停用，避免历史快照引用的 canonical 语义消失。 */
    @Transactional
    public CategoryMasterResponse deactivate(Long id) {
        if (id == null || mapper.deactivateById(id) != 1) {
            throw invalid("category_master id=" + id + " 不存在");
        }
        invalidateCache();
        return toResponse(requireRow(id));
    }

    /**
     * DB 有 active master 数据时以 DB 为唯一 alias 来源；表尚未迁移/为空时才回退 CategoryEnum。
     */
    public String resolveAlias(String value) {
        String key = trimToNull(value);
        if (key == null) return null;
        try {
            Map<String, String> index = activeAliasIndex();
            if (!index.isEmpty()) return index.get(key);
        } catch (Exception e) {
            log.warn("category_master 不可用，回退 CategoryEnum: {}", e.getMessage());
        }
        return CategoryEnum.fromAlias(key);
    }

    public boolean isActiveCanonical(String value) {
        String canonical = trimToNull(value);
        if (canonical == null) return false;
        try {
            Map<String, String> index = activeAliasIndex();
            if (!index.isEmpty()) return index.containsValue(canonical);
        } catch (Exception e) {
            log.warn("category_master 不可用，canonical 校验回退 CategoryEnum: {}", e.getMessage());
        }
        return CategoryEnum.isValid(canonical);
    }

    private Map<String, String> activeAliasIndex() {
        Map<String, String> cached = activeAliasIndex;
        if (cached != null) return cached;
        synchronized (this) {
            if (activeAliasIndex != null) return activeAliasIndex;
            Map<String, String> built = new LinkedHashMap<>();
            List<CategoryMaster> rows = mapper.selectAllActive();
            if (rows != null) {
                for (CategoryMaster row : rows) {
                    String canonical = trimToNull(row.getNameCanonical());
                    if (canonical == null) continue;
                    putIndex(built, canonical, canonical);
                    for (String alias : parseAliases(row.getAliases())) {
                        putIndex(built, alias, canonical);
                    }
                }
            }
            activeAliasIndex = Collections.unmodifiableMap(built);
            return activeAliasIndex;
        }
    }

    private static void putIndex(Map<String, String> index, String alias, String canonical) {
        String previous = index.putIfAbsent(alias, canonical);
        if (previous != null && !previous.equals(canonical)) {
            throw new IllegalStateException("active alias 冲突: " + alias + " → "
                    + previous + " / " + canonical);
        }
    }

    private void validateNoConflict(Long currentId, NormalizedInput input) {
        CategoryMaster sameCanonical = mapper.selectByCanonical(input.canonical());
        if (sameCanonical != null && !Objects.equals(sameCanonical.getId(), currentId)) {
            throw invalid("canonical 已存在: " + input.canonical());
        }

        Set<String> proposed = new LinkedHashSet<>();
        proposed.add(input.canonical());
        proposed.addAll(input.aliases());
        List<CategoryMaster> rows = mapper.selectAllActive();
        if (rows == null) return;
        for (CategoryMaster row : rows) {
            if (Objects.equals(row.getId(), currentId)) continue;
            Set<String> occupied = new LinkedHashSet<>();
            occupied.add(row.getNameCanonical());
            occupied.addAll(parseAliases(row.getAliases()));
            for (String value : proposed) {
                if (occupied.contains(value)) {
                    throw invalid("alias/canonical 已被 " + row.getNameCanonical() + " 使用: " + value);
                }
            }
        }
    }

    private NormalizedInput normalize(CategoryMasterUpsertRequest request) {
        if (request == null) throw invalid("请求体为空");
        String canonical = trimToNull(request.getNameCanonical());
        if (canonical == null) throw invalid("nameCanonical 必填");
        if (canonical.length() > 50) throw invalid("nameCanonical 最长 50 字符");
        if (request.getAliases() == null) throw invalid("aliases 必填");

        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String raw : request.getAliases()) {
            String alias = trimToNull(raw);
            if (alias == null) throw invalid("alias 不能为空");
            if (alias.length() > 100) throw invalid("alias 最长 100 字符: " + alias);
            if (!canonical.equals(alias)) aliases.add(alias);
        }
        return new NormalizedInput(canonical, List.copyOf(aliases),
                !Boolean.FALSE.equals(request.getActive()));
    }

    private CategoryMaster requireRow(Long id) {
        CategoryMaster row = mapper.selectById(id);
        if (row == null) throw new BusinessException(ErrorCode.DATABASE_ERROR,
                "category_master 写入后回读失败: id=" + id);
        return row;
    }

    private CategoryMasterResponse toResponse(CategoryMaster row) {
        return CategoryMasterResponse.builder()
                .id(row.getId())
                .nameCanonical(row.getNameCanonical())
                .aliases(parseAliases(row.getAliases()))
                .active(Boolean.TRUE.equals(row.getActive()))
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private String writeAliases(List<String> aliases) {
        try {
            return objectMapper.writeValueAsString(aliases);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "aliases JSON 序列化失败: " + e.getMessage());
        }
    }

    private List<String> parseAliases(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            List<String> normalized = new ArrayList<>();
            if (values != null) {
                for (String value : values) {
                    String trimmed = trimToNull(value);
                    if (trimmed != null) normalized.add(trimmed);
                }
            }
            return List.copyOf(normalized);
        } catch (Exception e) {
            throw new IllegalStateException("aliases JSON 非法: " + json, e);
        }
    }

    private void invalidateCache() {
        activeAliasIndex = null;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_CATEGORY_NAME, message);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record NormalizedInput(String canonical, List<String> aliases, boolean active) {}
}

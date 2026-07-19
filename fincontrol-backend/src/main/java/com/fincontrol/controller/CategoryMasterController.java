package com.fincontrol.controller;

import com.fincontrol.common.ApiResponse;
import com.fincontrol.dto.category.CategoryMasterResponse;
import com.fincontrol.dto.category.CategoryMasterUpsertRequest;
import com.fincontrol.service.CategoryMasterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 1a.10 七大类主数据 CRUD。 */
@RestController
@RequestMapping("/api/category-master")
public class CategoryMasterController {

    private final CategoryMasterService service;

    public CategoryMasterController(CategoryMasterService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CategoryMasterResponse>> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(service.list(includeInactive));
    }

    @PostMapping
    public ApiResponse<CategoryMasterResponse> create(
            @Valid @RequestBody CategoryMasterUpsertRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryMasterResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody CategoryMasterUpsertRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<CategoryMasterResponse> deactivate(@PathVariable("id") Long id) {
        return ApiResponse.success(service.deactivate(id));
    }
}

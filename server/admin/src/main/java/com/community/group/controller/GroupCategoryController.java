package com.community.group.controller;

import com.community.group.dto.request.GroupCategoryCreateRequest;
import com.community.group.dto.request.GroupCategoryOrderRequest;
import com.community.group.dto.request.GroupCategorySaveRequest;
import com.community.group.dto.response.GroupCategoriesResponse;
import com.community.group.dto.response.GroupCategoryItemView;
import com.community.group.service.GroupCategoryService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소모임 분류 API (docs/features/group-contract.md, admin-web {@code useGroupCategoryApi.ts} 머리말). 조회 {@code group.read}, 만들기·저장·순서·삭제 {@code group.settings}
 * (community 는 최고 관리자 전용 {@code ADMIN_ONLY} — 로보얌은 권한 코드로 위임한다. 없으면 403 {@code PERMISSION_DENIED}).
 */
@RestController
@RequestMapping("/api/admin/group-categories")
public class GroupCategoryController {

    private final GroupCategoryService service;

    public GroupCategoryController(GroupCategoryService service) {
        this.service = service;
    }

    @RequirePermission(Permission.GROUP_READ)
    @GetMapping
    public ApiResponse<GroupCategoriesResponse> list() {
        return ApiResponse.success(service.list());
    }

    @RequirePermission(Permission.GROUP_SETTINGS)
    @PostMapping
    public ResponseEntity<ApiResponse<GroupCategoryItemView>> create(@RequestBody GroupCategoryCreateRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 경로 {@code /order} 가 {@code /{categoryId}} 보다 먼저 맞도록 따로 둔다(문자 그대로의 경로가 이긴다) */
    @RequirePermission(Permission.GROUP_SETTINGS)
    @PutMapping("/order")
    public ApiResponse<GroupCategoriesResponse> order(@RequestBody GroupCategoryOrderRequest body, AdminActor actor) {
        return ApiResponse.success(service.order(actor, body));
    }

    @RequirePermission(Permission.GROUP_SETTINGS)
    @PutMapping("/{categoryId}")
    public ApiResponse<GroupCategoriesResponse> save(@PathVariable String categoryId, @RequestBody GroupCategorySaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, categoryId, body));
    }

    /** 소모임이 있으면 409 {@code GROUP_CATEGORY_NOT_EMPTY}(최상위 {@code groupCount}) */
    @RequirePermission(Permission.GROUP_SETTINGS)
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable String categoryId, AdminActor actor) {
        service.delete(actor, categoryId);
        return ResponseEntity.noContent().build();
    }
}

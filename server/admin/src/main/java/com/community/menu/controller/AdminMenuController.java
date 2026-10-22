package com.community.menu.controller;

import com.community.menu.dto.request.AdminMenuSaveRequest;
import com.community.menu.dto.response.AdminMenuConfigResponse;
import com.community.menu.service.AdminMenuService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 사이트 메뉴 구성 — 두 모드를 한 번에. 조회 {@code layout.read}, 저장 {@code admin-menu.manage} */
@RestController
@RequestMapping("/api/admin/admin-menu")
public class AdminMenuController {

    private final AdminMenuService service;

    public AdminMenuController(AdminMenuService service) {
        this.service = service;
    }

    @RequirePermission(Permission.LAYOUT_READ)
    @GetMapping
    public ApiResponse<AdminMenuConfigResponse> get(AdminActor actor) {
        return ApiResponse.success(service.get(actor));
    }

    /** 전체 교체 — 버전이 다르면 409 {@code MENU_VERSION_CONFLICT} */
    @RequirePermission(Permission.ADMIN_MENU_MANAGE)
    @PutMapping
    public ApiResponse<AdminMenuConfigResponse> save(@RequestBody AdminMenuSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

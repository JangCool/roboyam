package com.community.menu.controller;

import com.community.menu.dto.request.AdminMenuSaveRequest;
import com.community.menu.dto.response.AdminMenuConfigResponse;
import com.community.menu.service.AdminMenuService;
import com.community.permission.annotation.AdminAuthenticated;
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

    /**
     * 관리자 셸(사이드바·상단 메뉴·검색)이 그릴 메뉴 — 저장한 구성(없으면 레지스트리 기본 구성). 로그인한 운영자면 누구나 읽는다(권한 코드 없음 —
     * 셸은 모든 운영자에게 뜨고, 항목마다 권한으로 거르는 일은 화면이 화면 레지스트리의 권한으로 한다. 막는 것은 각 화면의 API 다). 모양은 조회와 같다.
     */
    @AdminAuthenticated
    @GetMapping("/shell")
    public ApiResponse<AdminMenuConfigResponse> shell(AdminActor actor) {
        return ApiResponse.success(service.get(actor));
    }

    /** 전체 교체 — 버전이 다르면 409 {@code MENU_VERSION_CONFLICT} */
    @RequirePermission(Permission.ADMIN_MENU_MANAGE)
    @PutMapping
    public ApiResponse<AdminMenuConfigResponse> save(@RequestBody AdminMenuSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

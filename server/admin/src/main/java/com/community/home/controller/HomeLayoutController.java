package com.community.home.controller;

import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.HomeLayoutBodyView;
import com.community.home.dto.response.HomeLayoutView;
import com.community.home.service.HomeLayoutAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테마별 홈 구성(설정 › 테마 › 레이아웃) — 조회는 site.read, 저장은 site.manage. 모양은 admin-web {@code useHomeLayoutApi.types.ts}. 모르는 테마는 404, 칸 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/home-layouts")
public class HomeLayoutController {

    private final HomeLayoutAdminService service;

    public HomeLayoutController(HomeLayoutAdminService service) {
        this.service = service;
    }

    /** 저장한 배치, 없으면 테마 기본 배치({@code updatedAt: null}) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/{theme}")
    public ApiResponse<HomeLayoutView> get(@PathVariable String theme) {
        return ApiResponse.success(service.get(theme));
    }

    /** 테마 기본 배치(초기화용 — 저장하지 않는다) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/{theme}/defaults")
    public ApiResponse<HomeLayoutBodyView> defaults(@PathVariable String theme) {
        return ApiResponse.success(service.defaults(theme));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{theme}")
    public ApiResponse<HomeLayoutView> save(@PathVariable String theme, @RequestBody(required = false) HomeLayoutSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, theme, body));
    }
}

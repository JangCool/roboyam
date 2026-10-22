package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteThemeRequest;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.service.SiteThemeService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 사이트 테마 API(설정 > 일반) — 조회는 site.read, 저장은 site.manage. 값 검사는 서비스가 한다({@code fields.theme}) */
@RestController
@RequestMapping("/api/admin/site/theme")
public class SiteThemeController {

    private final SiteThemeService service;

    public SiteThemeController(SiteThemeService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteThemeResponse> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteThemeResponse> save(@RequestBody SiteThemeRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

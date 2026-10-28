package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteThemeMenuRequest;
import com.community.site.dto.request.SiteThemeRequest;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.service.SiteThemeService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 사이트 테마·테마별 메뉴 설정 API(설정 › 테마 › 레이아웃) — 조회는 site.read, 저장은 site.manage. 값 검사는 서비스가 한다({@code fields.theme} · 메뉴 칸).
 */
@RestController
@RequestMapping("/api/admin/site")
public class SiteThemeController {

    private final SiteThemeService service;

    public SiteThemeController(SiteThemeService service) {
        this.service = service;
    }

    /** 지금 테마·고를 수 있는 목록 + {@code forTheme}(없으면 지금 테마)의 메뉴 설정 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/theme")
    public ApiResponse<SiteThemeResponse> load(@RequestParam(required = false) String forTheme) {
        return ApiResponse.success(service.load(forTheme));
    }

    /** 사이트 테마만({@code { theme }}) */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/theme")
    public ApiResponse<SiteThemeResponse> save(@RequestBody SiteThemeRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }

    /** 테마 하나의 메뉴 설정(배치·모양) 전부 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/themes/{id}/menu")
    public ApiResponse<SiteThemeResponse> saveMenu(@PathVariable String id, @RequestBody(required = false) SiteThemeMenuRequest body, AdminActor actor) {
        return ApiResponse.success(service.saveMenu(actor, id, body));
    }
}

package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteAboutRequest;
import com.community.site.dto.response.SiteAboutResponse;
import com.community.site.service.SiteAboutService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 소개 API(공개 사이트 푸터 「사이트 소개」 페이지) — 조회는 site.read, 저장은 site.manage. HTML 은 서비스가 소독한다({@code SiteAbouts}).
 */
@RestController
@RequestMapping("/api/admin/site/about")
public class SiteAboutController {

    private final SiteAboutService service;

    public SiteAboutController(SiteAboutService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteAboutResponse> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteAboutResponse> save(@RequestBody SiteAboutRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

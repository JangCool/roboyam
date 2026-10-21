package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteBasicRequest;
import com.community.site.dto.response.SiteBasicResponse;
import com.community.site.service.SiteBasicService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 기본 정보 API — 이름·한 줄 소개(+ 응답에 로고 주소). 조회는 site.read, 저장은 site.manage. 검사 문구가 칸별({@code fields.siteName} · {@code fields.tagline})이라 서비스가 검사한다.
 */
@RestController
@RequestMapping("/api/admin/site/basic")
public class SiteBasicController {

    private final SiteBasicService service;

    public SiteBasicController(SiteBasicService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteBasicResponse> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteBasicResponse> save(@RequestBody SiteBasicRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteSearchVerificationRequest;
import com.community.site.dto.response.SiteSearchVerificationResponse;
import com.community.site.service.SiteSearchVerificationService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 엔진 소유 확인 코드 API(설정 > 일반 「검색 엔진 등록」) — 조회는 site.read, 저장은 site.manage. 검사 문구가 칸별({@code fields.naver} 등)이라 서비스가 검사한다.
 */
@RestController
@RequestMapping("/api/admin/site/search-verification")
public class SiteSearchVerificationController {

    private final SiteSearchVerificationService service;

    public SiteSearchVerificationController(SiteSearchVerificationService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteSearchVerificationResponse> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteSearchVerificationResponse> save(@RequestBody SiteSearchVerificationRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

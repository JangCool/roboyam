package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteDisplayDefaultsRequest;
import com.community.site.dto.response.SiteDisplayDefaultsResponse;
import com.community.site.service.SiteDisplayService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 표시 기본값 API — 조회는 site.read, 저장은 site.manage. 칸별 검사 문구라 서비스가 검사한다 */
@RestController
@RequestMapping("/api/admin/site/display-defaults")
public class SiteDisplayController {

    private final SiteDisplayService service;

    public SiteDisplayController(SiteDisplayService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteDisplayDefaultsResponse> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteDisplayDefaultsResponse> save(@RequestBody SiteDisplayDefaultsRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

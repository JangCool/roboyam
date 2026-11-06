package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteNoticeBarRequest;
import com.community.site.dto.response.SiteNoticeBarSettingsResponse;
import com.community.site.service.SiteNoticeBarService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지 띠 설정 API(설정 > 일반 「공지 띠」) — 조회는 site.read, 저장은 site.manage. 검사 문구가 칸별({@code fields.customText} 등)이라 서비스가 검사한다.
 */
@RestController
@RequestMapping("/api/admin/site/notice-bar")
public class SiteNoticeBarController {

    private final SiteNoticeBarService service;

    public SiteNoticeBarController(SiteNoticeBarService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteNoticeBarSettingsResponse> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteNoticeBarSettingsResponse> save(@RequestBody SiteNoticeBarRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

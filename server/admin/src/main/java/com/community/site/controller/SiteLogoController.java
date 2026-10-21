package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteLogoFileRequest;
import com.community.site.dto.request.SvgSourceRequest;
import com.community.site.dto.response.SiteLogoResponse;
import com.community.site.service.SiteLogoService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 로고 API — 저장 단추 없이 즉시 적용되고 바뀐 {@code logoUrl} 만 돌려준다. 로고는 읽기 API 가 따로 없다(기본 정보 응답의 {@code logoUrl}). 모두 site.manage.
 * 그림 파일은 먼저 {@code POST /api/admin/files} 로 올린 뒤 {@code PUT} 으로 지정한다(바이트와 지정이 두 걸음).
 */
@RestController
@RequestMapping("/api/admin/site/logo")
public class SiteLogoController {

    private final SiteLogoService service;

    public SiteLogoController(SiteLogoService service) {
        this.service = service;
    }

    /** 올려 둔 그림 파일을 로고로 삼는다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteLogoResponse> setFile(@RequestBody SiteLogoFileRequest body, AdminActor actor) {
        return ApiResponse.success(service.setFile(actor, body == null ? null : body.fileId()));
    }

    /** SVG 소스를 로고로 삼는다 — 위험 요소가 있으면 정화하지 않고 거절한다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/svg")
    public ApiResponse<SiteLogoResponse> setSvg(@RequestBody SvgSourceRequest body, AdminActor actor) {
        return ApiResponse.success(service.setSvg(actor, body == null ? null : body.svgSource()));
    }

    /** 로고를 뗀다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping
    public ApiResponse<SiteLogoResponse> clear(AdminActor actor) {
        return ApiResponse.success(service.clear(actor));
    }
}

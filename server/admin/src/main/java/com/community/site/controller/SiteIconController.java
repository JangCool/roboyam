package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SvgSourceRequest;
import com.community.site.dto.response.SiteIconResponse;
import com.community.site.service.SiteIconService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사이트 아이콘 API — 한 걸음(서버가 여러 크기로 다시 만들어 저장한다). 조회는 site.read, 올리기·되돌리기는 site.manage. 응답은 모두 바뀐 상태 전체다.
 * 파일 칸이 없는 요청도 서비스가 {@code fields.file} 로 거절하도록 {@code required = false} 로 받는다.
 */
@RestController
@RequestMapping("/api/admin/site/icon")
public class SiteIconController {

    private final SiteIconService service;

    public SiteIconController(SiteIconService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteIconResponse> load() {
        return ApiResponse.success(service.get());
    }

    /** PNG 파일(multipart {@code file})로 아이콘을 올린다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping
    public ApiResponse<SiteIconResponse> upload(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ApiResponse.success(service.upload(actor, file));
    }

    /** SVG 소스로 아이콘을 올린다 — 서버가 PNG 로 바꿔 저장한다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/svg")
    public ApiResponse<SiteIconResponse> uploadSvg(@RequestBody SvgSourceRequest body, AdminActor actor) {
        return ApiResponse.success(service.uploadSvg(actor, body == null ? null : body.svgSource()));
    }

    /** 올려 둔 아이콘을 지우고 기본으로 되돌린다 — 이미 기본이면 그대로 200 */
    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping
    public ApiResponse<SiteIconResponse> reset(AdminActor actor) {
        return ApiResponse.success(service.reset(actor));
    }
}

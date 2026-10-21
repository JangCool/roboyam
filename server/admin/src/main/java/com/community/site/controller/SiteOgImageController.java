package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.response.SiteOgImageResponse;
import com.community.site.service.SiteOgImageService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 링크 미리보기 이미지(og:image) API — 사이트 아이콘과 같은 틀(한 걸음, 바뀐 상태 전체를 돌려준다). SVG 입구는 없다. 조회는 site.read, 올리기·되돌리기는 site.manage */
@RestController
@RequestMapping("/api/admin/site/og-image")
public class SiteOgImageController {

    private final SiteOgImageService service;

    public SiteOgImageController(SiteOgImageService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteOgImageResponse> load() {
        return ApiResponse.success(service.get());
    }

    /** PNG 파일(multipart {@code file})을 올린다 — 서버가 1200×630 불투명 PNG 로 맞춘다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping
    public ApiResponse<SiteOgImageResponse> upload(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ApiResponse.success(service.upload(actor, file));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping
    public ApiResponse<SiteOgImageResponse> reset(AdminActor actor) {
        return ApiResponse.success(service.reset(actor));
    }
}

package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteLogoBundleRequest;
import com.community.site.dto.request.SiteLogoRulesRequest;
import com.community.site.dto.response.SiteLogoLibraryResponse;
import com.community.site.service.SiteLogoLibraryService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로고 보관함 + 보이기 규칙 API(V249, docs/features/site-logo-library-contract.md) — 응답은 늘 묶음 목록 + 규칙 전체다. 묶음 삭제는 없다(보관).
 * 그림 파일은 먼저 {@code POST /api/admin/files}({@code purpose=SITE_LOGO})로 올린 뒤 묶음 요청의 칸에 {@code fileId} 로 넣는다. 읽기 site.read, 바꾸기 site.manage.
 */
@RestController
@RequestMapping("/api/admin/site/logo-library")
public class SiteLogoLibraryController {

    private final SiteLogoLibraryService service;

    public SiteLogoLibraryController(SiteLogoLibraryService service) {
        this.service = service;
    }

    /** 보관함과 보이기 규칙 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteLogoLibraryResponse> read() {
        return ApiResponse.success(service.read());
    }

    /** 새 묶음을 올린다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping("/bundles")
    public ApiResponse<SiteLogoLibraryResponse> createBundle(@RequestBody(required = false) SiteLogoBundleRequest body, AdminActor actor) {
        return ApiResponse.success(service.createBundle(actor, body));
    }

    /** 올린 묶음을 고친다(이름·칸·이름 포함) — 기본 제공 묶음이면 409 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/bundles/{id}")
    public ApiResponse<SiteLogoLibraryResponse> updateBundle(@PathVariable String id, @RequestBody(required = false) SiteLogoBundleRequest body, AdminActor actor) {
        return ApiResponse.success(service.updateBundle(actor, id, body));
    }

    /** 보이기 규칙(평소 로고·기념일·성별·기념일 로고 유형)을 통째로 저장한다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/rules")
    public ApiResponse<SiteLogoLibraryResponse> saveRules(@RequestBody(required = false) SiteLogoRulesRequest body, AdminActor actor) {
        return ApiResponse.success(service.saveRules(actor, body));
    }
}

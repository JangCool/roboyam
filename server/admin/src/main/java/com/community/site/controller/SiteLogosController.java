package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteLogoFileRequest;
import com.community.site.dto.request.SiteLogoNameRequest;
import com.community.site.dto.request.SvgSourceRequest;
import com.community.site.dto.response.SiteLogoSetResponse;
import com.community.site.service.SiteLogoService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 로고 4칸(언어 × 바탕) + 「로고에 사이트 이름이 들어 있음」 API(V237, docs/features/site-logo-variants-contract.md) — 저장 단추 없이 즉시 적용되고 매번 4칸 전체와
 * {@code nameInLogo} 를 돌려준다. 칸 이름 {@code {variant}} 는 {@code koLight}·{@code koDark}·{@code enLight}·{@code enDark}(모르는 이름은 {@code fields.variant} 400).
 * 그림 파일은 먼저 {@code POST /api/admin/files}({@code purpose=SITE_LOGO})로 올린 뒤 {@code PUT} 으로 지정한다(바이트와 지정이 두 걸음). 읽기 site.read, 바꾸기 site.manage.
 */
@RestController
@RequestMapping("/api/admin/site/logos")
public class SiteLogosController {

    private final SiteLogoService service;

    public SiteLogosController(SiteLogoService service) {
        this.service = service;
    }

    /** 4칸과 「이름 포함」 */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteLogoSetResponse> read() {
        return ApiResponse.success(service.read());
    }

    /** 「로고에 사이트 이름이 들어 있음」을 바꾼다 — 경로가 {@code /{variant}} 보다 구체적이라 먼저 고른다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/name-in-logo")
    public ApiResponse<SiteLogoSetResponse> setNameInLogo(@RequestBody SiteLogoNameRequest body, AdminActor actor) {
        return ApiResponse.success(service.setNameInLogo(actor, body == null ? null : body.nameInLogo()));
    }

    /** 올려 둔 그림 파일을 한 칸의 로고로 삼는다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{variant}")
    public ApiResponse<SiteLogoSetResponse> setFile(@PathVariable String variant, @RequestBody SiteLogoFileRequest body, AdminActor actor) {
        return ApiResponse.success(service.setFile(actor, variant, body == null ? null : body.fileId()));
    }

    /** SVG 소스를 한 칸의 로고로 삼는다 — 위험 요소가 있으면 정화하지 않고 거절한다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{variant}/svg")
    public ApiResponse<SiteLogoSetResponse> setSvg(@PathVariable String variant, @RequestBody SvgSourceRequest body, AdminActor actor) {
        return ApiResponse.success(service.setSvg(actor, variant, body == null ? null : body.svgSource()));
    }

    /** 한 칸의 로고를 뗀다 — 빈 칸은 화면이 대체 순서로 다른 칸을 쓴다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping("/{variant}")
    public ApiResponse<SiteLogoSetResponse> clear(@PathVariable String variant, AdminActor actor) {
        return ApiResponse.success(service.clear(actor, variant));
    }
}

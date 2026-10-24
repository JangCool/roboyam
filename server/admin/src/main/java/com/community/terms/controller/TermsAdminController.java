package com.community.terms.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.terms.dto.request.TermsDraftRequest;
import com.community.terms.dto.request.TermsPublishRequest;
import com.community.terms.dto.request.TermsSettingsRequest;
import com.community.terms.dto.response.TermsAdminOverviewResponse;
import com.community.terms.dto.response.TermsDraftView;
import com.community.terms.dto.response.TermsKindDetailResponse;
import com.community.terms.dto.response.TermsSettingsResponse;
import com.community.terms.dto.response.TermsVersionDetailResponse;
import com.community.terms.service.TermsAdminService;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 약관 API(docs/features/terms-contract.md) — 조회는 {@code terms.read}, 초안·게시·옵션은 {@code terms.manage}. 경로의 {@code {kind}} 는 {@code terms}·{@code privacy}(모르면 404).
 * {@code PUT /settings} 는 {@code /{kind}} 와 겹치지 않는다(메서드·깊이가 다르다).
 */
@RestController
@RequestMapping("/api/admin/terms")
public class TermsAdminController {

    private final TermsAdminService service;

    public TermsAdminController(TermsAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.TERMS_READ)
    @GetMapping
    public ApiResponse<TermsAdminOverviewResponse> overview() {
        return ApiResponse.success(service.overview());
    }

    @RequirePermission(Permission.TERMS_READ)
    @GetMapping("/{kind}")
    public ApiResponse<TermsKindDetailResponse> detail(@PathVariable String kind) {
        return ApiResponse.success(service.detail(kind));
    }

    @RequirePermission(Permission.TERMS_READ)
    @GetMapping("/{kind}/versions/{version}")
    public ApiResponse<TermsVersionDetailResponse> version(@PathVariable String kind, @PathVariable int version) {
        return ApiResponse.success(service.version(kind, version));
    }

    @RequirePermission(Permission.TERMS_MANAGE)
    @PutMapping("/{kind}/draft")
    public ApiResponse<TermsDraftView> saveDraft(@PathVariable String kind, @RequestBody TermsDraftRequest body, AdminActor actor) {
        return ApiResponse.success(service.saveDraft(actor, kind, body));
    }

    /** 초안 삭제 — 없어도 성공. 본문 없는 성공이라 204(다른 삭제 API 와 같다) */
    @RequirePermission(Permission.TERMS_MANAGE)
    @DeleteMapping("/{kind}/draft")
    public ResponseEntity<Void> deleteDraft(@PathVariable String kind, AdminActor actor) {
        service.deleteDraft(actor, kind);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TERMS_MANAGE)
    @PostMapping("/{kind}/publish")
    public ApiResponse<TermsVersionDetailResponse> publish(@PathVariable String kind, @RequestBody TermsPublishRequest body, AdminActor actor) {
        return ApiResponse.success(service.publish(actor, kind, body));
    }

    @RequirePermission(Permission.TERMS_MANAGE)
    @PutMapping("/settings")
    public ApiResponse<TermsSettingsResponse> saveSettings(@Valid @RequestBody TermsSettingsRequest body, AdminActor actor) {
        return ApiResponse.success(service.saveSettings(actor, body));
    }
}

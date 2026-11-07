package com.community.site.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.request.SiteOperatorRequest;
import com.community.site.dto.response.SiteOperatorResponse;
import com.community.site.service.SiteOperatorService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 주체 API(설정 > 일반 「운영 주체」, docs/features/terms-operator-contract.md) — 조회 site.read, 저장 site.manage. 칸별 문구 경로({@code fields.business.address} 등)가
 * 계약이라 서비스가 검사한다. 판매가 켜져 있는 동안 개인 단계로 바꾸거나 통신판매업 신고번호를 비우면 409 {@code COMMERCE_SALES_OPEN}.
 */
@RestController
@RequestMapping("/api/admin/site/operator")
public class SiteOperatorController {

    private final SiteOperatorService service;

    public SiteOperatorController(SiteOperatorService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<SiteOperatorResponse> load() {
        return ApiResponse.success(service.load());
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping
    public ApiResponse<SiteOperatorResponse> save(@RequestBody(required = false) SiteOperatorRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, body));
    }
}

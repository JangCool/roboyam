package com.community.commerce.controller;

import com.community.commerce.dto.request.RequestLimitsSaveRequest;
import com.community.commerce.dto.response.RequestLimitsView;
import com.community.commerce.service.CommerceRequestLimitService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커머스 설정(「설정 › 커머스 › 커머스 설정」, admin-web {@code useCommerceSettingsApi}) — 지금은 「요청 제한」 구획만(docs/features/commerce-foundation-contract.md 「0-5」).
 * 조회·저장 모두 {@code commerce.settings}. 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/commerce")
public class CommerceSettingsController {

    private final CommerceRequestLimitService limits;

    public CommerceSettingsController(CommerceRequestLimitService limits) {
        this.limits = limits;
    }

    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @GetMapping("/request-limits")
    public ResponseEntity<ApiResponse<RequestLimitsView>> requestLimits() {
        return noStore(limits.load());
    }

    /** 네 종류를 통째로 저장 — 저장된 값을 돌려준다 */
    @RequirePermission(Permission.COMMERCE_SETTINGS)
    @PutMapping("/request-limits")
    public ResponseEntity<ApiResponse<RequestLimitsView>> saveRequestLimits(@RequestBody(required = false) RequestLimitsSaveRequest body, AdminActor actor) {
        return noStore(limits.save(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}

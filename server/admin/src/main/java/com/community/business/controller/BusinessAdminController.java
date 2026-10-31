package com.community.business.controller;

import com.community.business.dto.BusinessAdminRequest;
import com.community.business.dto.BusinessAdminView;
import com.community.business.service.BusinessAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업자 심사(admin-web {@code useBusinessApi}, docs/features/business-contract.md) — 조회 {@code business.read} · 국세청 다시 조회·승인·반려·정지·등록증 사본 {@code business.manage} ·
 * 평문 열람 {@code business.reveal}(사유 5~200자) · 정지 해제 {@code business.unsuspend}. 응답은 {@code Cache-Control: no-store}. 상태가 이미 바뀌었으면 409
 * {@code BUSINESS_STATE_CHANGED}.
 */
@RestController
@RequestMapping("/api/admin/businesses")
public class BusinessAdminController {

    private final BusinessAdminService service;

    public BusinessAdminController(BusinessAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.BUSINESS_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<BusinessAdminView.Page>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String service,
                                                                    @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ok(this.service.list(status, service, q, page, size));
    }

    @RequirePermission(Permission.BUSINESS_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BusinessAdminView.Detail>> detail(@PathVariable String id) {
        return ok(service.detail(id));
    }

    /** 국세청 상태조회 다시 — 결과 스냅샷을 남기고 상세를 준다(B1) */
    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping("/{id}/nts-check")
    public ResponseEntity<ApiResponse<BusinessAdminView.Detail>> ntsCheck(@PathVariable String id, AdminActor actor) {
        return ok(service.recheckNts(actor, id));
    }

    /** 승인 — 계속사업자만. 휴·폐업 409 {@code BUSINESS_NTS_NOT_ACTIVE}, 조회 불가 + 사유 없음 400 {@code fields.ntsOverrideReason} */
    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<BusinessAdminView.Detail>> approve(@PathVariable String id, @RequestBody(required = false) BusinessAdminRequest.Approve body,
                                                                         AdminActor actor) {
        return ok(service.approve(actor, id, body));
    }

    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<BusinessAdminView.Detail>> reject(@PathVariable String id, @RequestBody(required = false) BusinessAdminRequest.Reject body,
                                                                        AdminActor actor) {
        return ok(service.reject(actor, id, body));
    }

    /** 정지(H2) — 진행 건 처리 선택은 저장·감사, 처리는 신청 건 백엔드 */
    @RequirePermission(Permission.BUSINESS_MANAGE)
    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<BusinessAdminView.Detail>> suspend(@PathVariable String id, @RequestBody(required = false) BusinessAdminRequest.Suspend body,
                                                                         AdminActor actor) {
        return ok(service.suspend(actor, id, body));
    }

    @RequirePermission(Permission.BUSINESS_UNSUSPEND)
    @PostMapping("/{id}/unsuspend")
    public ResponseEntity<ApiResponse<BusinessAdminView.Detail>> unsuspend(@PathVariable String id, @RequestBody(required = false) BusinessAdminRequest.Unsuspend body,
                                                                           AdminActor actor) {
        return ok(service.unsuspend(actor, id, body));
    }

    /** 평문 열람(B3) — 열람 기록(운영자·IP·사유) 뒤 값, 화면은 {@code hideAt} 에 가린다 */
    @RequirePermission(Permission.BUSINESS_REVEAL)
    @PostMapping("/{id}/reveal")
    public ResponseEntity<ApiResponse<BusinessAdminView.Reveal>> reveal(@PathVariable String id, @RequestBody(required = false) BusinessAdminRequest.Reveal body,
                                                                        AdminActor actor) {
        return ok(service.reveal(actor, id, body));
    }

    /** 사업자등록증 사본(B2) — 열 때마다 열람 기록 */
    @RequirePermission(Permission.BUSINESS_MANAGE)
    @GetMapping("/{id}/certificate")
    public ResponseEntity<ApiResponse<BusinessAdminView.CertificateFile>> certificate(@PathVariable String id, AdminActor actor) {
        return ok(service.certificate(actor, id));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}

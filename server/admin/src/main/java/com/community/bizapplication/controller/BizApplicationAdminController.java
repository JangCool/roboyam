package com.community.bizapplication.controller;

import com.community.bizapplication.dto.BizAdminRequest;
import com.community.bizapplication.dto.BizAdminView;
import com.community.bizapplication.service.BizApplicationAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 신청 건 심사(admin-web {@code useBizApplicationApi}, docs/features/biz-application-contract.md) — 조회 {@code business.read} · 심사·결제 처리 {@code ad.manage} ·
 * 환불 승인·거절·송금 완료 {@code deal-order.refund}. 응답은 {@code Cache-Control: no-store}. 상태가 이미 바뀌었으면 409 {@code BIZ_APPLICATION_STATE_CHANGED}.
 */
@RestController
@RequestMapping("/api/admin/biz-applications")
public class BizApplicationAdminController {

    private final BizApplicationAdminService service;

    public BizApplicationAdminController(BizApplicationAdminService service) {
        this.service = service;
    }

    /** 큐(REVIEW·PAYMENT·DEPOSIT·DEPOSIT_OVERDUE·LIVE·REFUND·CLOSED) 목록 + 큐별 건수 */
    @RequirePermission(Permission.BUSINESS_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<BizAdminView.Page>> list(@RequestParam(required = false) String queue, @RequestParam(required = false) String kind,
                                                               @RequestParam(required = false) String q) {
        return ok(service.list(queue, kind, q));
    }

    @RequirePermission(Permission.BUSINESS_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> detail(@PathVariable String id) {
        return ok(service.detail(BizApplicationAdminService.parseId(id)));
    }

    /** 기간 판정 — 요금표 계산 + 남은 자리(자기 자신 제외) */
    @RequirePermission(Permission.BUSINESS_READ)
    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<BizAdminView.Availability>> availability(@PathVariable String id, @RequestParam(required = false) String startAt,
                                                                               @RequestParam(required = false) String endAt) {
        return ok(service.availability(BizApplicationAdminService.parseId(id), startAt, endAt));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> approve(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Approve body,
                                                                    AdminActor actor) {
        return ok(service.approve(actor, BizApplicationAdminService.parseId(id), body));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> reject(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Reject body,
                                                                   AdminActor actor) {
        return ok(service.reject(actor, BizApplicationAdminService.parseId(id), body));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/manual")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> manual(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Manual body,
                                                                   AdminActor actor) {
        return ok(service.switchToManual(actor, BizApplicationAdminService.parseId(id), body));
    }

    /** 계약서 사본 — multipart {@code files}(1~10장) + {@code replaceId}(그 사본 하나를 대체됨으로, 이때 1장) */
    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/contracts")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> contracts(@PathVariable String id,
                                                                      @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                                                      @RequestParam(value = "replaceId", required = false) String replaceId, AdminActor actor) {
        return ok(service.uploadContracts(actor, BizApplicationAdminService.parseId(id), files, replaceId));
    }

    /** 계약서 사본 열람 — 열 때마다 처리 기록·감사 */
    @RequirePermission(Permission.AD_MANAGE)
    @GetMapping("/{id}/contracts/{fileId}")
    public ResponseEntity<ApiResponse<BizAdminView.ContractFile>> contract(@PathVariable String id, @PathVariable String fileId, AdminActor actor) {
        return ok(service.contract(actor, BizApplicationAdminService.parseId(id), BizApplicationAdminService.parseId(fileId)));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PutMapping("/{id}/deposit")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> deposit(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Deposit body,
                                                                    AdminActor actor) {
        return ok(service.saveDeposit(actor, BizApplicationAdminService.parseId(id), body));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> confirm(@PathVariable String id, AdminActor actor) {
        return ok(service.confirm(actor, BizApplicationAdminService.parseId(id)));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/deposit-deadline")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> depositDeadline(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Deadline body,
                                                                            AdminActor actor) {
        return ok(service.extendDeposit(actor, BizApplicationAdminService.parseId(id), body));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> cancel(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Reason body,
                                                                   AdminActor actor) {
        return ok(service.cancel(actor, BizApplicationAdminService.parseId(id), body));
    }

    @RequirePermission(Permission.DEAL_ORDER_REFUND)
    @PostMapping("/{id}/refund/approve")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> approveRefund(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.RefundApprove body,
                                                                          AdminActor actor) {
        return ok(service.approveRefund(actor, BizApplicationAdminService.parseId(id), body));
    }

    @RequirePermission(Permission.DEAL_ORDER_REFUND)
    @PostMapping("/{id}/refund/deny")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> denyRefund(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Reason body,
                                                                       AdminActor actor) {
        return ok(service.denyRefund(actor, BizApplicationAdminService.parseId(id), body));
    }

    /** 수동 환불 송금 완료 표시(설계 추가) */
    @RequirePermission(Permission.DEAL_ORDER_REFUND)
    @PostMapping("/{id}/refund/transfer")
    public ResponseEntity<ApiResponse<BizAdminView.Detail>> transfer(@PathVariable String id, @RequestBody(required = false) BizAdminRequest.Transfer body,
                                                                     AdminActor actor) {
        return ok(service.markTransferred(actor, BizApplicationAdminService.parseId(id), body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}

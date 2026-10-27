package com.community.payment.controller;

import com.community.payment.dto.request.PaymentReasonRequest;
import com.community.payment.dto.response.PaymentAnomalyPageView;
import com.community.payment.dto.response.PaymentCancelView;
import com.community.payment.dto.response.PaymentOrderDetailView;
import com.community.payment.dto.response.PaymentOrderRowView;
import com.community.payment.dto.response.PaymentPageView;
import com.community.payment.dto.response.PaymentRecheckView;
import com.community.payment.dto.response.PaymentWebhookRowView;
import com.community.payment.dto.response.ReconcileDayView;
import com.community.payment.dto.response.ReconcileIssueView;
import com.community.payment.dto.response.ReconcileOverviewView;
import com.community.payment.service.PaymentAdminService;
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
 * 관리자 결제 API(커머스 2a-3) — admin-web {@code usePaymentAdminApi.ts} 머리말 그대로(docs/features/commerce-payment-contract.md 「2a-1 화면 목업」·결정 12).
 * 권한: 조회(결제 주문·웹훅 수신 내역·결제 이상·대사) {@code commerce.payment.read}, 처리(다시 시도·수동 처리 완료·포트원에서 다시 확인·대사 이슈 처리·이 날 다시 대사)
 * {@code commerce.reconcile.manage}. 회원·돈 정보가 담긴 응답이라 {@code Cache-Control: no-store}. 쪽 번호는 0부터, 기간은 ISO 순간 [from, to).
 */
@RestController
@RequestMapping("/api/admin")
public class PaymentAdminController {

    private final PaymentAdminService service;

    public PaymentAdminController(PaymentAdminService service) {
        this.service = service;
    }

    /** 결제 주문 목록 — 만든 시각 내림차순, {@code anomaly} 는 종류 또는 {@code ANY}, {@code q} 는 결제 번호 일부 또는 회원 번호 */
    @RequirePermission(Permission.COMMERCE_PAYMENT_READ)
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<PaymentPageView<PaymentOrderRowView>>> orders(@RequestParam(required = false) String status,
                                                                                    @RequestParam(required = false) String anomaly,
                                                                                    @RequestParam(required = false) String orderType,
                                                                                    @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                                    @RequestParam(required = false) String q, @RequestParam(required = false) String page,
                                                                                    @RequestParam(required = false) String size) {
        return noStore(service.orders(status, anomaly, orderType, from, to, q, page, size));
    }

    /** 결제 상세 — 열람 감사({@code PAYMENT_ORDER_VIEW}) · 404 {@code PAYMENT_ORDER_NOT_FOUND} */
    @RequirePermission(Permission.COMMERCE_PAYMENT_READ)
    @GetMapping("/payments/{id}")
    public ResponseEntity<ApiResponse<PaymentOrderDetailView>> order(@PathVariable String id, AdminActor actor) {
        return noStore(service.order(actor, id));
    }

    /** 포트원에서 다시 확인 — 사유 · 503 {@code PAYMENT_GATEWAY_UNAVAILABLE} */
    @RequirePermission(Permission.COMMERCE_RECONCILE_MANAGE)
    @PostMapping("/payments/{id}/recheck")
    public ResponseEntity<ApiResponse<PaymentRecheckView>> recheck(@PathVariable String id, @RequestBody(required = false) PaymentReasonRequest body, AdminActor actor) {
        return noStore(service.recheck(actor, id, body));
    }

    /** 웹훅 수신 내역 — 받은 시각 내림차순, {@code q} 는 결제 번호·webhook-id 일부 */
    @RequirePermission(Permission.COMMERCE_PAYMENT_READ)
    @GetMapping("/payment-webhooks")
    public ResponseEntity<ApiResponse<PaymentPageView<PaymentWebhookRowView>>> webhooks(@RequestParam(required = false) String result,
                                                                                        @RequestParam(required = false) String from,
                                                                                        @RequestParam(required = false) String to,
                                                                                        @RequestParam(required = false) String q,
                                                                                        @RequestParam(required = false) String page,
                                                                                        @RequestParam(required = false) String size) {
        return noStore(service.webhooks(result, from, to, q, page, size));
    }

    /** 결제 이상(이상 결제 + 멈췄던 취소) — 기본 처리 안 된 것만 */
    @RequirePermission(Permission.COMMERCE_PAYMENT_READ)
    @GetMapping("/payment-anomalies")
    public ResponseEntity<ApiResponse<PaymentAnomalyPageView>> anomalies(@RequestParam(required = false) String kind,
                                                                         @RequestParam(required = false) String includeResolved,
                                                                         @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                         @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return noStore(service.anomalies(kind, includeResolved, from, to, page, size));
    }

    /** 멈춘 취소 다시 시도(GAVE_UP → PENDING) — 409 {@code PAYMENT_CANCEL_NOT_GAVE_UP} */
    @RequirePermission(Permission.COMMERCE_RECONCILE_MANAGE)
    @PostMapping("/payment-cancels/{id}/retry")
    public ResponseEntity<ApiResponse<PaymentCancelView>> retryCancel(@PathVariable String id, @RequestBody(required = false) PaymentReasonRequest body,
                                                                      AdminActor actor) {
        return noStore(service.retryCancel(actor, id, body));
    }

    /** 멈춘 취소 수동 처리 완료(GAVE_UP → MANUAL, 취소 누계 불변) — 409 {@code PAYMENT_CANCEL_NOT_GAVE_UP} */
    @RequirePermission(Permission.COMMERCE_RECONCILE_MANAGE)
    @PostMapping("/payment-cancels/{id}/manual")
    public ResponseEntity<ApiResponse<PaymentCancelView>> manualCancel(@PathVariable String id, @RequestBody(required = false) PaymentReasonRequest body,
                                                                       AdminActor actor) {
        return noStore(service.manualCancel(actor, id, body));
    }

    /** 날짜별 대사 요약(어제부터 14일) */
    @RequirePermission(Permission.COMMERCE_PAYMENT_READ)
    @GetMapping("/payment-reconciliation")
    public ResponseEntity<ApiResponse<ReconcileOverviewView>> reconciliation() {
        return noStore(service.reconciliation());
    }

    /** 대사 불일치 이슈 — 열린 것 먼저 */
    @RequirePermission(Permission.COMMERCE_PAYMENT_READ)
    @GetMapping("/payment-reconciliation/issues")
    public ResponseEntity<ApiResponse<PaymentPageView<ReconcileIssueView>>> issues(@RequestParam(required = false) String type,
                                                                                   @RequestParam(required = false) String status,
                                                                                   @RequestParam(required = false) String day,
                                                                                   @RequestParam(required = false) String page,
                                                                                   @RequestParam(required = false) String size) {
        return noStore(service.issues(type, status, day, page, size));
    }

    /** 대사 이슈 처리 — 409 {@code RECONCILE_ISSUE_NOT_OPEN} */
    @RequirePermission(Permission.COMMERCE_RECONCILE_MANAGE)
    @PostMapping("/payment-reconciliation/issues/{id}/resolve")
    public ResponseEntity<ApiResponse<ReconcileIssueView>> resolveIssue(@PathVariable String id, @RequestBody(required = false) PaymentReasonRequest body,
                                                                        AdminActor actor) {
        return noStore(service.resolveIssue(actor, id, body));
    }

    /** 이 날 다시 대사(비동기 — 배치 요청 표) — 사유 없음 · 409 {@code RECONCILE_ALREADY_RUNNING} · 400 {@code fields.day}(오늘·미래·못 읽음) */
    @RequirePermission(Permission.COMMERCE_RECONCILE_MANAGE)
    @PostMapping("/payment-reconciliation/days/{day}/rerun")
    public ResponseEntity<ApiResponse<ReconcileDayView>> rerunDay(@PathVariable String day, AdminActor actor) {
        return noStore(service.rerunDay(actor, day));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}

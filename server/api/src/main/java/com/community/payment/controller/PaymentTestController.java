package com.community.payment.controller;

import com.community.auth.dto.AuthUser;
import com.community.payment.testorder.PaymentTestOrderService;
import com.community.payment.testorder.PaymentTestViews.CreateRequest;
import com.community.payment.testorder.PaymentTestViews.CreatedView;
import com.community.payment.testorder.PaymentTestViews.FakePayRequest;
import com.community.payment.testorder.PaymentTestViews.StatusView;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 시험(docs/features/commerce-payment-contract.md 결정 7) — user-web {@code /dev/payment} 가 쓴다. <b>{@code roboyam.payment.test-orders=true} 인 서버에만 있다</b>
 * (local·dev, prod 에서 켜면 기동하지 않는다 — 꺼져 있으면 주소가 없어 404). 로그인 필요·CSRF·{@code no-store}, 그 회원의 시험 결제만.
 *
 * <ul>
 *   <li>{@code POST /api/dev/payments/test-orders} {@code {amount, decision: ACCEPT|REJECT|ERROR}} → 201 {@code {orderRefId, paymentId, orderName, amount, currency, expiresAt,
 *       provider, storeId?, channelKey?}} · 400 {@code fields.amount}(100~1,000,000) · 503 {@code PAYMENT_NOT_CONFIGURED}</li>
 *   <li>{@code POST /api/dev/payments/{paymentId}/fake-pay} {@code {result: PAID|FAILED, amount?}} → 204 — 가짜 게이트웨이일 때만(포트원이면 404). 이어서 완료 API 를 부른다</li>
 *   <li>{@code GET /api/dev/payments/{paymentId}} → {@code {paymentId, orderRefId, status, anomaly, amount, paidAmount, canceledAmount, handlerCash, cancels[]}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dev/payments")
@ConditionalOnProperty(prefix = "roboyam.payment", name = "test-orders", havingValue = "true")
public class PaymentTestController {

    private final PaymentTestOrderService tests;

    public PaymentTestController(PaymentTestOrderService tests) {
        this.tests = tests;
    }

    @PostMapping("/test-orders")
    public ResponseEntity<ApiResponse<CreatedView>> create(Authentication authentication, @RequestBody CreateRequest body) {
        CreatedView view = tests.create(member(authentication), body.amount(), body.decision());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.success(view));
    }

    @PostMapping("/{paymentId}/fake-pay")
    public ResponseEntity<Void> fakePay(Authentication authentication, @PathVariable String paymentId, @Valid @RequestBody FakePayRequest body) {
        tests.fakePay(member(authentication), paymentId, body.result(), body.amount());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<StatusView>> status(Authentication authentication, @PathVariable String paymentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(tests.status(member(authentication), paymentId)));
    }

    private static long member(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal.id();
    }
}

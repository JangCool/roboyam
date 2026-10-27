package com.community.payment.controller;

import com.community.auth.dto.AuthUser;
import com.community.payment.dto.request.PaymentCompleteRequest;
import com.community.payment.dto.response.PaymentCompletionView;
import com.community.payment.service.PaymentCompletionService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 완료 API(docs/features/commerce-payment-contract.md 결정 6) — 화면이 결제창을 마친 뒤 부른다. 로그인 필요(없으면 401), CSRF, {@code Cache-Control: no-store}.
 * 결제 시작 API 는 도메인마다 따로 있다(공구 참여·충전 — 시험은 {@code /api/dev/payments}).
 *
 * <ul>
 *   <li>{@code POST /api/payments/{paymentId}/complete} {@code {orderType, orderRefId}} → {@code {paymentId, orderType, orderRefId, status, accepted, anomaly, amount, paidAmount, paidAt}}
 *       · 404 {@code PAYMENT_ORDER_NOT_FOUND}(없음·남의 결제) · 409 {@code PAYMENT_ORDER_MISMATCH}(다른 주문) · 409 {@code PAYMENT_NOT_PAID}(아직 결제 전 — 다시 시도)
 *       · 503 {@code PAYMENT_GATEWAY_UNAVAILABLE} · 503 {@code PAYMENT_NOT_CONFIGURED} · 400 {@code VALIDATION}</li>
 * </ul>
 * 속도 제한·멱등 키는 두지 않는다(commerce.md 12-4 — 상태를 새로 만들지 않는 확인 요청, 확정 코드가 조건부 UPDATE 로 한 번만 확정한다).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentCompletionService completion;

    public PaymentController(PaymentCompletionService completion) {
        this.completion = completion;
    }

    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<ApiResponse<PaymentCompletionView>> complete(Authentication authentication, @PathVariable String paymentId,
                                                                       @Valid @RequestBody PaymentCompleteRequest body) {
        PaymentCompletionView view = completion.complete(member(authentication), paymentId, body.orderType(), Long.parseLong(body.orderRefId()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(view));
    }

    private static long member(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal.id();
    }
}

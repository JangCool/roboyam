package com.community.emoticon.controller;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.support.MoneyRequestGuard;
import com.community.emoticon.dto.response.EmoticonPurchaseView;
import com.community.emoticon.service.EmoticonPurchaseService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 로보티콘 구매 내역·셀프 환불(emoticon-contract S3 — user-web {@code useMyEmoticonsApi} 의 구매 부분). 로그인 필요(401), {@code no-store}.
 * <ul>
 *   <li>{@code GET /api/me/emoticon-purchases} → {@code { items }}(결제 완료·환불 — 최근 결제 먼저, 본인 것만)</li>
 *   <li>{@code POST /api/me/emoticon-purchases/{id}/refund} + 머리글 {@code Idempotency-Key}(필수) → {@code { id, status: REFUNDED, refundedAt, canceledAmount }}.
 *       {@link MoneyRequestGuard}(요청 제한 {@code REFUND_REQUEST} · 같은 키 다시 보내기는 처음 응답 · 같은 키 다른 구매 {@code 409 IDEMPOTENCY_KEY_REUSED}).
 *       없음·남의 것 404 {@code EMOTICON_PURCHASE_NOT_FOUND} · 이미 환불 409 {@code EMOTICON_ALREADY_REFUNDED} · 썼거나 기한 지남 409 {@code EMOTICON_REFUND_NOT_ALLOWED}(최상위 {@code blockedBy})</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/emoticon-purchases")
public class MyEmoticonPurchaseController {

    private final EmoticonPurchaseService purchases;
    private final MoneyRequestGuard guard;

    public MyEmoticonPurchaseController(EmoticonPurchaseService purchases, MoneyRequestGuard guard) {
        this.purchases = purchases;
        this.guard = guard;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EmoticonPurchaseView.MyPurchaseList>> mine(Authentication authentication) {
        return EmoticonController.noStore(purchases.mine(EmoticonController.userId(authentication)));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<Object>> refund(@PathVariable String id, @RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String idempotencyKey,
                                                      Authentication authentication) {
        long userId = EmoticonController.userId(authentication);
        return guard.handle(userId, idempotencyKey, RequestAction.REFUND_REQUEST, Map.of("emoticonPurchaseId", id), () -> purchases.selfRefund(userId, id));
    }
}

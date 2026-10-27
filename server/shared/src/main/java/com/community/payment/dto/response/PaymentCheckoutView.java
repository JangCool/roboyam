package com.community.payment.dto.response;

import com.community.payment.dto.PaymentCheckout;
import com.community.payment.gateway.PaymentGatewayKind;

/**
 * 결제창을 여는 데 필요한 값 — 도메인의 결제 시작 API(공구 참여·충전·시험 주문)가 같이 주는 <b>공용 모양</b>(2a-1 화면 계약 {@code PaymentCheckout}).
 * 화면은 이 값 그대로 결제창을 연다(금액을 다시 계산하지 않는다, commerce.md 8-2).
 *
 * @param gateway    FAKE(가짜 결제창) · PORTONE(포트원 브라우저 SDK)
 * @param storeId    포트원 상점 ID(가짜면 null)
 * @param channelKey 포트원 결제 채널 키(가짜면 null)
 */
public record PaymentCheckoutView(PaymentGatewayKind gateway, String paymentId, String orderName, long amount, String currency, String storeId, String channelKey) {

    public static PaymentCheckoutView of(PaymentCheckout checkout) {
        return new PaymentCheckoutView(checkout.provider(), checkout.paymentId(), checkout.orderName(), checkout.amount(), checkout.currency(), checkout.storeId(),
                checkout.channelKey());
    }
}

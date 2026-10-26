package com.community.payment.gateway;

/** 결제창을 열 때 화면에 줄 값 — 포트원이면 상점 ID·결제 채널 키(공개 값), 가짜면 둘 다 null(화면은 가짜 결제창을 띄운다) */
public record PaymentCheckoutInfo(PaymentGatewayKind provider, String storeId, String channelKey) {
}

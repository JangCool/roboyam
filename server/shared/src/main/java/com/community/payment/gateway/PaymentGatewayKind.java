package com.community.payment.gateway;

/** 결제 게이트웨이 구현 — 배포 설정 {@code roboyam.payment.gateway}(portone | fake). 가짜는 local·시험 전용(dev·prod 에서는 기동을 막는다) */
public enum PaymentGatewayKind {
    PORTONE,
    FAKE
}

package com.community.zconfiguration.security;

/** 결제 웹훅 주소 — 보안 설정(로그인·CSRF 없이 받기)과 컨트롤러가 같은 값을 쓴다(docs/features/commerce-payment-contract.md 결정 5) */
public final class PaymentWebhookPaths {

    /** 포트원 V2 웹훅 — 포트원 콘솔에 {사이트 공개 주소}{@value} 로 등록한다 */
    public static final String PORTONE = "/api/payments/webhooks/portone";

    private PaymentWebhookPaths() {
    }
}

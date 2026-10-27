package com.community.payment.gateway;

/** 웹훅 서명 검증 결과(Standard Webhooks) — OK 가 아니면 아무것도 바꾸지 않고 포트원 조회도 하지 않는다 */
public enum WebhookVerification {
    OK,
    /** webhook-id · webhook-timestamp · webhook-signature 중 빠진 것이 있다 */
    MISSING_HEADERS,
    /** 시각 머리글이 숫자가 아니거나 지금과 5분 넘게 차이 난다(재전송 공격 막기) */
    TIMESTAMP_SKEW,
    /** 서명이 맞지 않는다 */
    BAD_SIGNATURE,
    /** 웹훅 비밀키가 설정되지 않았거나 형식이 틀렸다 — 검증할 수 없다(설정한 뒤 포트원 재전송을 받으려고 503) */
    NOT_CONFIGURED
}

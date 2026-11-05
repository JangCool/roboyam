package com.community.emoticon.entity;

/**
 * 유료 팩 구매 기록의 상태(V144 {@code ck_emoticon_purchases_status}) — 결제 준비 한 번이 한 줄이다.
 * {@code PENDING}(결제 대기) → {@code PAID}(결제 완료 — 보유) / {@code CLOSED}(새 결제 준비가 대신했거나 결제가 거절됐다) · {@code PAID} → {@code REFUNDED}(환불 — 보유 해제).
 * 회원·관리자 화면에는 {@code PAID}·{@code REFUNDED} 만 나온다(목업 {@code EmoticonPurchaseStatus}·{@code EmoticonSaleStatus}).
 */
public enum EmoticonPurchaseStatus {
    PENDING,
    CLOSED,
    PAID,
    REFUNDED
}

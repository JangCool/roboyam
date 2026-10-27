package com.community.payment.dto;

/** 결제 이상({@code payment_orders.anomaly}) — 이상 결제는 「받아들인 결제」로 세지 않고(결정 3) 전액 자동 취소한다 */
public enum PaymentAnomaly {
    /** 포트원 결제 금액·통화가 우리가 청구한 것과 다르다 — 도메인 확정 없음, 운영자 알림 */
    AMOUNT_MISMATCH,
    /** 도메인 처리기가 거절했다(공구 마감·자격 상실 등) */
    DOMAIN_REJECTED,
    /** 유효 시각이 지난 뒤 결제됐다(결정 4) — 도메인 확정 없음 */
    LATE_PAYMENT
}

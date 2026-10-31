package com.community.payment.dto;

/**
 * 결제 주문의 종류({@code payment_orders.order_type}) — 결제 모듈은 도메인을 모르고 이 종류로 처리기({@code PaymentOrderHandler})를 고른다.
 * 이름은 DB 값이라 한 번 정하면 바꾸지 않는다.
 */
public enum PaymentOrderType {
    /** 포인트 충전(3단계) */
    POINT_CHARGE,
    /** 공구 참여(2b) */
    GROUP_BUY,
    /** 공구 반품비 부족분 추가 결제(2b-3, 공구 계약 결정 68 — 대상 = 반품 요청, 선점 없음) */
    GROUP_BUY_RETURN_FEE,
    /** 사업자 신청 건(광고·핫딜) 온라인 결제(V112 — 대상 = 신청 건, 금액 = 승인 확정 합계, 처리기 {@code BizApplicationPaymentHandler}) */
    BIZ_APPLICATION,
    /** 결제 모듈 시험 주문(local·dev 의 {@code roboyam.payment.test-orders} — prod 에서 켜면 기동하지 않는다) */
    PAYMENT_TEST
}

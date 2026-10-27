package com.community.payment.dto;

import java.time.Duration;

/**
 * 도메인이 결제 주문을 만들 때 주는 값 — 금액은 도메인이 서버에서 계산한 값이다(요청 본문의 금액을 받지 않는다, commerce.md 3절).
 *
 * @param userId     회원
 * @param orderType  주문 종류
 * @param orderRefId 도메인 주문 ID(충전 건·참여 건 — 같은 주문의 재결제는 같은 값)
 * @param orderName  결제창에 보일 주문명(1~100자 — PG 마다 더 짧게 자른다: 나이스·이니시스 40바이트 등, 포트원 문서)
 * @param amount     청구 금액(원, 1 이상)
 * @param validFor   유효 시간(1~60분 — 선점 시간 등). 지나면 늦은 결제
 */
public record PaymentOrderRequest(long userId, PaymentOrderType orderType, long orderRefId, String orderName, long amount, Duration validFor) {
}

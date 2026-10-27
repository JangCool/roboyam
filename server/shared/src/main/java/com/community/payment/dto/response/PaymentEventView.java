package com.community.payment.dto.response;

import java.time.Instant;

/**
 * 결제 이력 한 줄 — {@code detail} 은 서버가 요청 언어로 푼 한 줄(개인정보·비밀값 없음, 확정한 목업 가정 6), 웹훅에서 온 줄은 {@code providerEventId} 에 webhook-id.
 *
 * @param kind 이력 종류({@code PaymentEventStore.Kind} 이름)
 */
public record PaymentEventView(String id, String kind, String detail, String providerEventId, Instant createdAt) {
}

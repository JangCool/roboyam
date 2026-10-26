package com.community.pointshop.dto.response;

import java.time.Instant;

/**
 * 환불 기록(환불된 줄만).
 *
 * @param kind         {@code SELF}(회원 셀프) · {@code EXCEPTION}(운영자 예외 환불) · {@code REQUEST}(정지 중 환불 신청을 운영자가 수락)
 * @param operatorName 처리 운영자 이름(예외 환불·신청 수락 — 셀프·지워진 운영자는 null)
 */
public record PointShopPurchaseRefundView(String kind, Instant refundedAt, String operatorName, String reason, long refundedPoints) {
}

package com.community.pointshop.dto.response;

import com.community.pointshop.entity.PointShopRefundRequestStatus;
import java.time.Instant;

/**
 * 환불 신청(정지 중 셀프 환불 — V87) — 내 아이템 줄·관리자 구매 줄의 {@code refundRequest}(그 구매의 가장 최근 신청, 없으면 null).
 *
 * @param decidedAt    수락·거절 시각(대기면 null)
 * @param rejectReason 거절 사유(거절만)
 */
public record PointShopRefundRequestView(String id, PointShopRefundRequestStatus status, Instant requestedAt, Instant decidedAt, String rejectReason) {
}

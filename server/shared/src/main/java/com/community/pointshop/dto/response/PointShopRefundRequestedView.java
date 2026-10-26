package com.community.pointshop.dto.response;

import com.community.pointshop.entity.PointShopRefundRequestStatus;
import java.time.Instant;

/** 정지 중 셀프 환불 = 환불 신청을 만들었다 — {@code POST /api/me/items/{id}/refund} 의 202 응답 {@code { requestId, status: PENDING, requestedAt }} */
public record PointShopRefundRequestedView(String requestId, PointShopRefundRequestStatus status, Instant requestedAt) {
}

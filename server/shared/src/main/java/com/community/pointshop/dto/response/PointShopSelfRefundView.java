package com.community.pointshop.dto.response;

import java.time.Instant;

/**
 * 셀프 환불 판정(완료 줄만) — 기한은 <b>지금</b> 설정의 일수로 계산한다.
 *
 * @param blockedBy {@code USED}(썼음) · {@code EXPIRED}(기한 지남). 되면 null
 */
public record PointShopSelfRefundView(boolean allowed, Instant until, String blockedBy) {
}

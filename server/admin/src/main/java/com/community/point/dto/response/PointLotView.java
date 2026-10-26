package com.community.point.dto.response;

import java.time.Instant;

/**
 * 받은 포인트 한 묶음({@code point_lots} 한 줄) — admin-web {@code PointLot}. ID 는 문자열.
 *
 * @param kind             {@code FREE}(무상·적립) · {@code PAID}(유상·충전)
 * @param expiresAt        소멸 시각 — 무상은 늘 있고 유상은 null
 * @param sourceReasonCode 출처 사유 코드(원장 사유 또는 전환 {@code MIGRATION})
 * @param sourceReason     요청 언어로 푼 출처 이름(번들 {@code point.reason.<코드>}, 모르는 코드면 코드 그대로)
 * @param restored         만료된 묶음을 되돌려 새로 만든 묶음인가({@code restored_from_lot_id} 있음)
 * @param expired          소멸일이 지났나(소멸 시각 ≤ 지금)
 */
public record PointLotView(String lotId, String kind, long originalAmount, long remainingAmount, Instant expiresAt, String sourceReasonCode, String sourceReason,
                           boolean restored, Instant createdAt, boolean expired) {
}

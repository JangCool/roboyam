package com.community.point.dto.response;

import java.util.List;

/** {@code GET /api/me/points/pending} — 확정 대기(대기 중인 보류 지급만, 확정 예정이 가까운 것부터) */
public record MyPointPendingView(List<MyPointPendingItemView> items) {
}

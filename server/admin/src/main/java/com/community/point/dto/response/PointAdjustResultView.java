package com.community.point.dto.response;

/**
 * {@code POST /api/admin/members/{id}/points} — admin-web {@code PointAdjustResponse}.
 *
 * @param applied 실제 반영된 값 — 차감은 0P 까지라 요청보다 작을 수 있고, 0 이면 원장 줄·알림이 없다
 * @param balance 반영 뒤 잔액
 */
public record PointAdjustResultView(long applied, long balance) {
}

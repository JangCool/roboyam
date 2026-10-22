package com.community.membernotification.dto.response;

/**
 * 포인트 회수 알림의 칸(community {@code points}).
 *
 * @param kind   {@code ADMIN_ADJUST}(운영 조정)
 * @param amount 실제로 회수된 포인트(양수)
 * @param note   운영 조정의 사유(회원에게 공개되는 문구) — 없으면 null
 */
public record NotificationPointsView(String kind, long amount, String note) {
}

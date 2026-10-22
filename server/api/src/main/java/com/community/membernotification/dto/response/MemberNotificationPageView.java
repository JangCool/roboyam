package com.community.membernotification.dto.response;

import java.util.List;

/**
 * {@code GET /api/notifications} — 최신이 위, 커서 쪽.
 *
 * @param nextCursor    다음 쪽의 {@code cursor} — 끝이면 null
 * @param unreadCount   거르기와 상관없는 전체 안 읽은 수(상한 100)
 * @param retentionDays 지금 적용되는 회원 알림 보관 일수(게시판 기본 설정 「알림」)
 */
public record MemberNotificationPageView(List<MemberNotificationItemView> items, boolean hasNext, String nextCursor, int unreadCount, int retentionDays) {
}

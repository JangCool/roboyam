package com.community.notification.dto.response;

import java.util.List;

/**
 * 알림 목록 한 쪽(최근순). {@code nextCursor} 가 있으면 다음 쪽의 {@code cursor} 로 쓴다(없으면 끝). {@code unread} 는 목록 쪽과 상관없이 내가 볼 수 있는 안 읽은 알림의 총수 —
 * {@link NotificationSummaryView#unread()} 와 같은 값이다.
 */
public record NotificationPageView(List<NotificationItemView> items, String nextCursor, long unread) {
}

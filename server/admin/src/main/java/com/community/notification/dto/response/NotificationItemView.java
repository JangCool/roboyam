package com.community.notification.dto.response;

import java.time.Instant;

/**
 * 종 알림 서랍의 알림 한 건. ID 는 문자열(Snowflake). 제목·본문은 요청 언어({@code Accept-Language})로 푼 글이고 본문이 없으면 {@code null}, 링크가 없으면 {@code null}.
 *
 * @param kind {@code info} · {@code success} · {@code warning} · {@code critical}
 * @param area 이벤트의 영역 id(예: {@code system} · {@code security} · {@code member})
 */
public record NotificationItemView(String id, String eventCode, String area, String kind, String title, String body, NotificationLink link, Instant createdAt,
                                   boolean read) {
}

package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * {@code GET /api/admin/notification-templates/mail} 의 한 줄.
 *
 * @param audience      쓰는 대상 {@code MEMBER|OPERATOR|ANY}
 * @param headerColor   머리말 띠 색(팔레트) — null 이면 띠 없음
 * @param messageCount  이 템플릿을 쓰는 문구 수(기본 문구 포함) — 0 일 때만 지울 수 있다
 * @param messageCounts 받는 사람별({@code MEMBER}·{@code OPERATOR}) 쓰는 문구 수
 */
public record MailTemplateListItemView(String id, String name, boolean builtin, String audience, String headerColor, int messageCount,
                                       Map<String, Integer> messageCounts, Instant updatedAt, String updatedByName) {
}

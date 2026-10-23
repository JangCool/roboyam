package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * {@code GET /api/admin/notification-templates/{slack|mattermost}} 의 한 줄.
 *
 * @param type         {@code SLACK} · {@code MATTERMOST}
 * @param colors       심각도별({@code INFO}·{@code WARNING}·{@code CRITICAL}) 색(팔레트)
 * @param messageCount 이 템플릿을 쓰는 문구 수(기본 문구 포함)
 */
public record ChatTemplateListItemView(String id, String type, String name, boolean builtin, Map<String, String> colors, int messageCount, Instant updatedAt,
                                       String updatedByName) {
}

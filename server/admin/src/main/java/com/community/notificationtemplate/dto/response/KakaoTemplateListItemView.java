package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * {@code GET /api/admin/notification-templates/kakao} 의 한 줄 — 준비 중(읽기만).
 *
 * @param templateCode 카카오에 등록한 알림톡 템플릿 코드
 * @param status       {@code REQUESTED|APPROVED|REJECTED}
 */
public record KakaoTemplateListItemView(String id, String name, String templateCode, String status, int messageCount, Map<String, Integer> messageCounts,
                                        Instant updatedAt, String updatedByName) {
}

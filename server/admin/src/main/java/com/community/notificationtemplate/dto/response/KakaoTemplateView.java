package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/admin/notification-templates/kakao/{id}} — 승인 본문 · 변수 대응표 · 버튼 · 발신 프로필 이름(준비 중 — 읽기만).
 *
 * @param mappings 변수 대응 {@code [{placeholder, variable, sample}]}
 * @param buttons  버튼 {@code [{name, linkVariable}]}
 */
public record KakaoTemplateView(String id, String name, String templateCode, String status, int messageCount, Map<String, Integer> messageCounts, Instant updatedAt,
                                String updatedByName, String approvedBody, String emphasis, List<Map<String, Object>> mappings, List<Map<String, Object>> buttons,
                                String channelName) {
}

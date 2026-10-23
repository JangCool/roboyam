package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/admin/notification-templates/{slack|mattermost}/{id}} — 목록 한 줄의 칸 + 카드 모양 칸.
 *
 * @param openLabel 언어별 「열기」 버튼 글자
 * @param frames    언어별 본문 틀(그 앱 표기 + {@code {{content}}} 한 번)
 * @param variables 틀에서 쓸 수 있는 변수 — 본문 자리 {@code content} + 운영자 공통 변수
 */
public record ChatTemplateView(String id, String type, String name, boolean builtin, Map<String, String> colors, int messageCount, Instant updatedAt,
                               String updatedByName, boolean showSeverityIcon, boolean boldTitle, boolean showSiteName, boolean showSentAt, boolean showOpenButton,
                               Map<String, String> openLabel, Map<String, String> frames, String siteName, List<MessageVariableView> variables) {
}

package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/admin/notification-templates/mail/{id}} — 메일 한 통이 이 틀 안에 들어간다({@code frames} 의 {@code {{content}}} 자리에 문구 본문).
 *
 * @param footer    언어별 꼬리말(평문 + 공통 변수)
 * @param frames    언어별 틀(간단 서식 HTML, {@code {{content}}} 정확히 한 번)
 * @param logoUrl   사이트 설정의 로고 주소(없으면 null)
 * @param variables 틀·꼬리말에서 쓸 수 있는 변수 — 쓰는 대상별({@code MEMBER}·{@code OPERATOR}·{@code ANY}: 공통 변수 + 본문 자리 {@code content})
 */
public record MailTemplateView(String id, String name, boolean builtin, String audience, boolean showLogo, boolean showSiteName, String headerColor,
                               Map<String, String> footer, Map<String, String> frames, int messageCount, Map<String, Integer> messageCounts, Instant updatedAt,
                               String updatedByName, String logoUrl, String siteName, Map<String, List<MessageVariableView>> variables) {
}

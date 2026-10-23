package com.community.notificationtemplate.dto.request;

import java.util.Map;

/**
 * {@code POST·PUT /api/admin/notification-templates/{slack|mattermost}[/{id}]} — 종류는 주소가 정한다.
 *
 * @param colors    심각도({@code INFO}·{@code WARNING}·{@code CRITICAL}) → 팔레트 색 이름
 * @param openLabel 언어별 「열기」 버튼 글자(보일 때 필수·20자)
 * @param frames    언어별 본문 틀(그 앱 표기, {@code {{content}}} 한 번, 500자)
 */
public record ChatTemplateSaveRequest(String name, Boolean showSeverityIcon, Boolean boldTitle, Map<String, String> colors, Boolean showSiteName, Boolean showSentAt,
                                      Boolean showOpenButton, Map<String, String> openLabel, Map<String, String> frames) {
}

package com.community.membernotification.dto.request;

import java.util.List;

/** {@code PUT /api/me/notification-settings} — 검사 순서·칸 경로({@code events[i].code})가 계약이라 Bean Validation 없이 서비스가 본다 */
public record MyNotificationSettingsRequest(List<MyNotificationSettingInput> events) {
}

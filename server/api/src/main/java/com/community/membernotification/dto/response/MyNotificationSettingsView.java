package com.community.membernotification.dto.response;

import java.util.List;

/**
 * {@code GET}·{@code PUT /api/me/notification-settings} 응답(user-web {@code MyNotificationSettings}).
 *
 * @param events             회원 이벤트 카탈로그 중 준비된 것, 카탈로그 차례
 * @param emailReceivable    이메일이 있고 인증을 마쳤는가(회원 알림 메일 발송기와 같은 판정)
 * @param emailBlockedReason {@code NO_EMAIL} · {@code NOT_VERIFIED} · null
 */
public record MyNotificationSettingsView(List<MyNotificationSettingView> events, boolean emailReceivable, String emailBlockedReason) {
}

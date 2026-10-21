package com.community.notification.dto.response;

/**
 * 내 알림 설정의 이벤트 한 줄 — 내가 볼 수 있는(권한이 있는) 이벤트만, 준비 중인 이벤트는 없다.
 *
 * @param muted     내가 껐는가(필수 이벤트는 늘 false)
 * @param emailable 이 이벤트가 지금 이메일로도 전달되는가(코드가 이메일을 허용하고 이벤트 관리에서 이메일 전달이 켜져 있을 때)
 */
public record MyNotificationEventView(String code, String area, String name, String description, String severity, boolean mandatory, boolean muted,
                                      boolean emailable) {
}

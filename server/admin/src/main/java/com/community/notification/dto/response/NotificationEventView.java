package com.community.notification.dto.response;

/**
 * 이벤트 관리 화면의 이벤트 한 줄. 정의(영역·심각도·수신 대상·필수·이메일 가능)는 코드에 고정이고 운영자가 바꾸는 것은 {@code enabled}·{@code emailEnabled} 뿐이다.
 *
 * @param recipientText   누가 받는지 한 줄 설명(요청 언어)
 * @param emailable       이메일로 보낼 수 있는 이벤트인가(false 면 {@code emailEnabled} 는 늘 false)
 * @param featureReady    발행처가 있는가(false 면 「준비 중」 — 설정을 바꿀 수 없다)
 * @param sentLast30Days  최근 30일 동안 만들어진 알림 수(받은 사람마다 한 건씩 센다)
 */
public record NotificationEventView(String code, String area, String severity, String name, String description, String recipientText, boolean mandatory,
                                    boolean emailable, boolean enabled, boolean emailEnabled, boolean featureReady, long sentLast30Days) {
}

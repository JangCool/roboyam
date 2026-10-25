package com.community.membernotification.dto.response;

/**
 * 알림 설정 한 줄(user-web {@code MyNotificationSetting}).
 *
 * @param area             {@code content} · {@code member} · {@code safety}
 * @param name             번들 {@code member.event.<코드>.name}(요청 언어)
 * @param inAppOffered     운영자 설정 사용 && 앱 알림
 * @param emailOffered     운영자 설정 사용 && 이메일
 * @param memberCanTurnOff 코드 잠금이 없고 운영자 「회원이 끌 수 있음」이 켜짐
 * @param lockReason       코드 잠금 까닭({@code ACCOUNT}·{@code ACTION}) — 없으면 null
 * @param inApp            회원 선택 — 잠긴 이벤트·운영자가 안 보내는 채널은 늘 true
 * @param email            회원 선택 — 같은 규칙
 */
public record MyNotificationSettingView(String code, String area, String name, String description, boolean inAppOffered, boolean emailOffered,
                                        boolean memberCanTurnOff, String lockReason, boolean inApp, boolean email) {
}

package com.community.member.dto.response;

/**
 * 회원 알림 설정 한 줄 — 회원 이벤트({@code MemberEvent}) 하나.
 *
 * @param code   이벤트 코드
 * @param name   이벤트 이름(요청 언어 — 번들 {@code member.event.<코드>.name})
 * @param inApp  앱 알림 받기 — 회원이 고른 값(줄이 없으면 켬). 잠긴 이벤트는 늘 true
 * @param email  이메일로도 받기 — 같은 규칙
 * @param locked 회원이 끌 수 없는가 — 코드 잠금(계정·처리 알림) 또는 운영자 「회원이 끌 수 있음」 꺼짐
 */
public record MemberNotificationSettingView(String code, String name, Boolean inApp, Boolean email, boolean locked) {
}

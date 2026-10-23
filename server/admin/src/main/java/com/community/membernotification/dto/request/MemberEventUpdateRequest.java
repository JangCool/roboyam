package com.community.membernotification.dto.request;

/** 회원 이벤트 하나의 사용 여부 · 앱 알림 · 이메일 · 회원이 끌 수 있음 저장 — 네 칸 모두 필요하다(없으면 400 {@code fields.<칸>}) */
public record MemberEventUpdateRequest(Boolean enabled, Boolean inApp, Boolean email, Boolean memberCanTurnOff) {
}

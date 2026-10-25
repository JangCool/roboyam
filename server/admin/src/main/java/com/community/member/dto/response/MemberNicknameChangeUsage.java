package com.community.member.dto.response;

/**
 * 닉네임 변경 횟수 — 가입 뒤 바꾼 횟수(V66 {@code profiles.nickname_change_count})와 무료로 바꿀 수 있는 횟수(서버 상수 {@code ProfileEntity.NICKNAME_FREE_LIMIT}).
 */
public record MemberNicknameChangeUsage(int changeCount, int freeLimit) {
}

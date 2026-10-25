package com.community.profile.dto.response;

/**
 * 닉네임 변경 규칙의 지금 상태 — 가입 뒤 {@code freeLimit}(서버 상수 1)번만 바꿀 수 있다(날짜 대기 없음).
 *
 * @param allowed     지금 바꿀 수 있는가({@code changeCount < freeLimit})
 * @param changeCount 가입 뒤 바꾼 횟수(가입 때 닉네임은 세지 않는다)
 * @param freeLimit   가입 뒤 바꿀 수 있는 횟수
 */
public record NicknameChangeView(boolean allowed, int changeCount, int freeLimit) {
}

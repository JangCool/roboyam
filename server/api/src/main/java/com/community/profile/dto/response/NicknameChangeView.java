package com.community.profile.dto.response;

/**
 * 닉네임 변경 규칙의 지금 상태 — 가입 뒤 {@code freeLimit}(서버 상수 1)번은 무료로 바꾸고(날짜 대기 없음), 그 뒤로는 바꿀 때마다 닉네임 변경권 1장을 쓴다.
 *
 * @param allowed     지금 무료로 바꿀 수 있는가({@code changeCount < freeLimit})
 * @param changeCount 가입 뒤 바꾼 횟수(가입 때 닉네임은 세지 않는다 — 변경권으로 바꾼 것도 센다)
 * @param freeLimit   가입 뒤 무료로 바꿀 수 있는 횟수
 * @param ticketCount 안 쓴 닉네임 변경권 장수(포인트 상점 — 커머스 1-2)
 * @param ticketPrice 변경권 한 장 값(P) — 포인트 상점 변경권 품목 가격(결정 2), 품목이 없으면 null
 */
public record NicknameChangeView(boolean allowed, int changeCount, int freeLimit, int ticketCount, Integer ticketPrice) {
}

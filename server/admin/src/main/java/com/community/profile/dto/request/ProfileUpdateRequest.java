package com.community.profile.dto.request;

/**
 * 내 정보 수정 — 바꿀 수 있는 것은 닉네임뿐이다(아이디(이메일)·역할은 못 바꾼다). 검사 문구가 칸별이라 서비스가 검사한다({@code fields.nickname}).
 * 앞뒤를 자르지 않은 그대로 받는다 — 앞뒤·연속 공백은 허용하지 않는다(화면 {@code util-validation.ts} 와 같다).
 */
public record ProfileUpdateRequest(String nickname) {
}

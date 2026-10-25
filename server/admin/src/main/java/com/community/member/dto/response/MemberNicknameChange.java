package com.community.member.dto.response;

import java.time.Instant;

/** 닉네임 변경 이력 한 줄 — {@code profile_nickname_history}(V66). 회원 상세에 최신이 위로 최근 20줄 */
public record MemberNicknameChange(String oldNickname, String newNickname, Instant changedAt) {
}

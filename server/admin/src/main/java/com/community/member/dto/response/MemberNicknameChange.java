package com.community.member.dto.response;

import java.time.Instant;

/** 닉네임 변경 이력 한 줄 — 이력 표가 아직 없어 늘 빈 목록으로 내려간다 */
public record MemberNicknameChange(String oldNickname, String newNickname, Instant changedAt) {
}

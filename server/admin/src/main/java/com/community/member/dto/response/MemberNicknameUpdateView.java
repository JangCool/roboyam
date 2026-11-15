package com.community.member.dto.response;

import java.time.Instant;

/**
 * 운영자 회원 닉네임 바꾸기 결과 — 화면은 이 응답 뒤 회원 상세를 다시 불러온다.
 *
 * @param memberId         회원 ID(문자열)
 * @param nickname         바뀐 닉네임
 * @param previousNickname 바뀌기 전 닉네임
 * @param changedAt        바꾼 시각
 */
public record MemberNicknameUpdateView(String memberId, String nickname, String previousNickname, Instant changedAt) {
}

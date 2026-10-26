package com.community.point.dto.response;

/**
 * 조정 대상 회원 후보 — admin-web {@code PointMemberCandidate}.
 *
 * @param email       {@code member.pii} 가 없으면 가린 값({@code ab***@gmail.com}). 이메일이 없으면 null
 * @param balance     지금 잔액(합계 = 무상 + 유상)
 * @param freeBalance 그중 무상(적립) — 조정은 이것만 바꾼다
 * @param paidBalance 그중 유상(충전)
 */
public record PointMemberCandidateView(String memberId, String nickname, String email, long balance, long freeBalance, long paidBalance) {
}

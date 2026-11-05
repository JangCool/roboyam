package com.community.member.dto.response;

/**
 * 회원 상세의 「운영자 회원 계정」 표시(admin-web {@code MemberOperatorAccountRef}, operator-member-account-contract) — 이 회원 계정이 이어진 운영자.
 *
 * @param active 힘이 살아 있나 — 거짓이면 「멈춤」(운영자 계정을 쓸 수 없거나 회원 계정 정지 중)
 */
public record MemberOperatorAccountRef(String operatorId, String operatorName, boolean active) {
}

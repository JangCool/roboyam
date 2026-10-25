package com.community.withdrawal.dto.request;

/**
 * 탈퇴 회원 연락처 열람({@code POST /api/admin/members/{memberId}/withdrawn-contact/reveal}) — 열람 사유 5~200자, 검사는 서비스({@code fields.reason}). 사유는 감사에 남는다.
 */
public record WithdrawnContactRevealRequest(String reason) {
}

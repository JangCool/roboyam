package com.community.operator.dto.request;

/** {@code PUT /api/admin/operators/{operatorId}/member-account} 본문 — 이을 회원 ID(문자열). 같은 이메일이 아니어도 되고(2026-11-24), 서버가 연결 자격(정책 3)을 다시 판정한다 */
public record OperatorMemberLinkRequest(String userId) {
}

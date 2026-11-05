package com.community.operator.dto.request;

/** {@code PUT /api/admin/operators/{operatorId}/member-account} 본문 — 이을 회원 ID(문자열). 서버가 후보 조건을 다시 판정한다 */
public record OperatorMemberLinkRequest(String userId) {
}

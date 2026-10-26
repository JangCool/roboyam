package com.community.restriction.dto.request;

/**
 * 기능 제한 해제({@code POST …/restrictions/{id}/lift}) — 해제 사유 5~200자(화면 확정 결정 2), 검사는 서비스({@code fields.reason}).
 */
public record MemberRestrictionLiftRequest(String reason) {
}

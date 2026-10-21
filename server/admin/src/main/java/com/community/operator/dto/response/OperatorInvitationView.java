package com.community.operator.dto.response;

import java.time.Instant;
import java.util.List;

/** 대기 중인 초대 한 건 — 아직 수락하지 않은 초대 링크. 토큰은 싣지 않는다 */
public record OperatorInvitationView(String invitationId, String email, List<OperatorRoleRef> roles, Instant invitedAt, Instant expiresAt, boolean expired) {
}

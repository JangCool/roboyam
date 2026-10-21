package com.community.operator.dto.response;

import java.time.Instant;
import java.util.List;

/** 유효한 초대 링크의 내용 — 초대받은 이메일(계정의 로그인 아이디가 된다)·받을 역할(비었을 수 있다)·링크가 유효한 마지막 시각 */
public record InvitationCheckResponse(String email, List<OperatorRoleRef> roles, Instant expiresAt) {
}

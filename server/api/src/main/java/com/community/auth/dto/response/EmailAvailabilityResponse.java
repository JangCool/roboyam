package com.community.auth.dto.response;

/** {@code GET /api/auth/email-available} — 그 이메일로 가입할 수 있는가(이미 가입된 회원이 없는가) */
public record EmailAvailabilityResponse(boolean available) {
}

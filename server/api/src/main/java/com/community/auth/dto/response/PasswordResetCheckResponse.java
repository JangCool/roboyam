package com.community.auth.dto.response;

/** {@code GET /api/auth/password/reset/check} — 재설정 링크를 아직 쓸 수 있다(쓸 수 없으면 400 {@code TOKEN_INVALID}·410 {@code TOKEN_EXPIRED}) */
public record PasswordResetCheckResponse(boolean valid) {
}

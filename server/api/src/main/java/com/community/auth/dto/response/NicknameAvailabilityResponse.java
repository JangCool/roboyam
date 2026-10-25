package com.community.auth.dto.response;

/**
 * {@code GET /api/auth/nickname-available} — 그 닉네임을 쓸 수 있는가.
 *
 * @param reason 쓸 수 없는 까닭 {@code LENGTH} · {@code CHARS} · {@code RESERVED} · {@code DUPLICATE}(이 차례로 본다). 쓸 수 있으면 null
 */
public record NicknameAvailabilityResponse(boolean available, String reason) {
}

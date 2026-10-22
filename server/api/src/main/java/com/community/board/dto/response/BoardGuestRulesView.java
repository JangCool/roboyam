package com.community.board.dto.response;

/**
 * 비회원 글·댓글 규칙(user-web {@code BoardGuestRules}) — 관리자 보안 설정의 공개해도 되는 값.
 *
 * @param passwordMax 비밀번호 최대 길이 — 관리자 설정에 없는 값이라 서버 상수 20
 */
public record BoardGuestRulesView(boolean captchaRequired, int nameMin, int nameMax, int passwordMin, int passwordMax, int linkLimit,
                                  boolean imageUploadAllowed) {
}

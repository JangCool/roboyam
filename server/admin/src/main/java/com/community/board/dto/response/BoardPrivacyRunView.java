package com.community.board.dto.response;

/** 지금 실행 결과 — processed = 바꾼 글·댓글 행 수 */
public record BoardPrivacyRunView(String axis, long processed) {
}

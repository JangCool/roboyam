package com.community.board.dto.response;

import java.time.Instant;

/** 파기 대상 한 줄 — id 는 post-(id) · comment-(id), title 은 글 제목 또는 댓글 앞 40자 */
public record BoardPrivacyTargetView(String id, String title, String boardName, Instant createdAt) {
}

package com.community.rewardevent.dto.request;

import java.time.Instant;

/** 댓글 후보 미리 보기(저장 전에도 쓴다) — 글 주소·번호 · 기간 · 글 작성자 제외 · 운영자 회원 계정 제외 */
public record RewardCommentPreviewRequest(String postRef, Instant from, Instant to, Boolean excludeAuthor, Boolean excludeOperators) {
}

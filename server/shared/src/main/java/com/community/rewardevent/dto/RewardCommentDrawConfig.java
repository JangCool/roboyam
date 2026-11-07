package com.community.rewardevent.dto;

import java.time.Instant;

/**
 * 댓글 추첨 설정(admin-web {@code RewardEventCommentDrawConfig}) — 글 주소 또는 번호(운영자가 넣은 그대로) · 서버가 찾은 글(요청에서는 읽지 않는다) · 댓글 기간 · 글 작성자 제외.
 *
 * @param from 비어 있으면 이벤트 시작 시각
 * @param to   비어 있으면 이벤트 끝 시각
 */
public record RewardCommentDrawConfig(String postRef, String postId, String postTitle, Instant from, Instant to, Boolean excludeAuthor) {
}

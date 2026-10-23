package com.community.post.entity;

/**
 * 글·댓글 본문 형식 — V56 의 {@code posts.content_format}·{@code comments.content_format}. 요청에서 빠지면 {@link #TEXT}(예전 클라이언트 호환).
 * HTML 은 서버가 허용 목록으로 정화한 것만 저장하고, 공개 화면은 그 HTML 을 그대로 그린다.
 */
public enum ContentFormat {
    /** 평문(줄바꿈 {@code \n}) */
    TEXT,
    /** 서버가 정화한 HTML */
    HTML
}

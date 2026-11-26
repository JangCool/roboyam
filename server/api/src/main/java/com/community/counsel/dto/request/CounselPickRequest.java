package com.community.counsel.dto.request;

/** {@code PUT /api/posts/{id}/counsel/pick} 본문(user-web {@code CounselPickRequest}) — 고른 답의 댓글 ID(문자열) */
public record CounselPickRequest(String commentId) {
}

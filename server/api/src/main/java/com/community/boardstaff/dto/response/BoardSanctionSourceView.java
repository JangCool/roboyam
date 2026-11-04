package com.community.boardstaff.dto.response;

/** 계기 글·댓글 — {@code title} 은 줄 때의 글 제목(없으면 null) */
public record BoardSanctionSourceView(String postId, String commentId, String title) {
}

package com.community.comment.dto.response;

/** 답한 상대(답글의 답글) — 이름은 그 댓글쓴이(지운 댓글이면 빈 글자) */
public record ReplyToView(String id, String name) {
}

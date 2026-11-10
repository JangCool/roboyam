package com.community.comment.dto.response;

import com.community.post.dto.response.PostAuthorView;

/**
 * 답한 상대(답글의 답글) — 이름은 그 댓글쓴이(지운 댓글이면 빈 글자).
 *
 * @param anonymous 답한 댓글이 익명 댓글(V184)이면 그 표시(글쓴이·글 안 번호 — {@code name} 은 「익명3」 같은 표시 문구), 아니면 null
 */
public record ReplyToView(String id, String name, PostAuthorView.AuthorAnonymousView anonymous) {
}

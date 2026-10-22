package com.community.comment.dto.request;

import com.community.board.dto.request.GuestCredentials;

/**
 * {@code POST /api/posts/{id}/comments}(user-web {@code CommentWriteRequest}).
 *
 * @param parentId  답글이면 원댓글 ID
 * @param replyToId 답글의 답글이면 그 댓글 ID(이름 표시용) — 원댓글이거나 같은 원댓글 아래의 답글이어야 한다
 * @param guest     비회원이면 필수
 */
public record CommentWriteRequest(String content, String parentId, String replyToId, GuestCredentials guest) {
}

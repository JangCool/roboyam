package com.community.profile.dto.response;

import java.time.Instant;

/**
 * 작성 댓글 한 줄(user-web {@code AuthorCommentRow}).
 *
 * @param bodyPreview 댓글의 보이는 글자 앞 100자(줄바꿈·연속 공백은 공백 한 칸, 넘으면 {@code …})
 * @param reply       답글인가
 */
public record AuthorCommentRowView(String id, String postId, String postTitle, CommentBoardRefView board, String bodyPreview, int likeCount, boolean reply,
                                   Instant createdAt) {
}

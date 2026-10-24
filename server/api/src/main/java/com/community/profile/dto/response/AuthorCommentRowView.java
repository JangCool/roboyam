package com.community.profile.dto.response;

import java.time.Instant;

/**
 * 작성 댓글 한 줄(user-web {@code AuthorCommentRow}).
 *
 * @param bodyPreview 댓글의 보이는 글자 앞 100자(줄바꿈·연속 공백은 공백 한 칸, 넘으면 {@code …})
 * @param reply       답글인가
 * @param blocked     보는 사람 기준 차단({@code USER}·{@code WORD} — 댓글 목록과 같은 판정: 단어는 댓글 본문, 내 댓글·비로그인 null). 빼지 않고 표시만 한다
 */
public record AuthorCommentRowView(String id, String postId, String postTitle, CommentBoardRefView board, String bodyPreview, int likeCount, boolean reply,
                                   Instant createdAt, String blocked) {
}

package com.community.comment.dto.response;

import com.community.post.dto.response.PostAuthorView;
import java.time.Instant;

/**
 * 댓글 하나(user-web {@code CommentView}).
 *
 * @param parentId  원댓글 ID — 원댓글이면 null
 * @param content   본문 — {@code state} 가 VISIBLE 일 때만, 아니면 null
 * @param state     VISIBLE · DELETED(답글이 있어 자리만) · HIDDEN(신고로 숨김) · DELAYED(비회원에게 늦게 보임 — 본문을 보내지 않는다)
 * @param updatedAt 고쳤으면 그 시각
 */
public record CommentView(String id, String postId, String parentId, ReplyToView replyTo, PostAuthorView author, String content, String state,
                          Instant createdAt, Instant updatedAt, int likeCount, boolean liked, boolean mine) {
}

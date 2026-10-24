package com.community.comment.dto.response;

import com.community.post.dto.response.PostAuthorView;
import java.time.Instant;
import java.util.List;

/**
 * 원댓글 + 그 아래 답글(오래된순) — user-web {@code CommentThread}. 칸은 {@link CommentView} 와 같고(차단 표시 {@code blocked}·입장 배지 {@code stance}·숨김 까닭 {@code hiddenBy} 포함) {@code replies} 가 더해진다
 * ({@code stance}·{@code hiddenBy} 는 {@code replies} 앞에 온다 — JSON 은 이름으로 읽으므로 차례는 상관없다).
 */
public record CommentThreadView(String id, String postId, String parentId, ReplyToView replyTo, PostAuthorView author, String content, String contentFormat,
                                String state, Instant createdAt, Instant updatedAt, int likeCount, boolean liked, boolean mine, String blocked, String stance,
                                String hiddenBy, List<CommentView> replies) {

    public static CommentThreadView of(CommentView top, List<CommentView> replies) {
        return new CommentThreadView(top.id(), top.postId(), top.parentId(), top.replyTo(), top.author(), top.content(), top.contentFormat(), top.state(),
                top.createdAt(), top.updatedAt(), top.likeCount(), top.liked(), top.mine(), top.blocked(), top.stance(), top.hiddenBy(), replies);
    }
}

package com.community.comment.dto.response;

import com.community.post.dto.response.PostAuthorView;
import java.time.Instant;

/**
 * 댓글 하나(user-web {@code CommentView}).
 *
 * @param parentId  원댓글 ID — 원댓글이면 null
 * @param content   본문 — {@code state} 가 VISIBLE 일 때만, 아니면 null
 * @param contentFormat 본문 형식 TEXT · HTML(간단 에디터 — 서버가 정화한 HTML)
 * @param state     VISIBLE · DELETED(답글이 있어 자리만) · HIDDEN(신고로 숨김) · DELAYED(비회원에게 늦게 보임 — 본문을 보내지 않는다)
 * @param updatedAt 고쳤으면 그 시각
 * @param blocked   보는 사람이 작성 회원을 차단했으면 {@code USER}, 본문에 차단 단어가 들었으면 {@code WORD}, 아니면 null(내 댓글·비로그인·보이지 않는 댓글은 늘 null)
 * @param stance    하이라이트 토론의 입장 배지 PRO · CON, 일반 댓글은 null — 쓸 때 정해진 값이라 입장을 바꿔도 그대로다. 답글·지운 자리({@code DELETED})·숨긴 자리({@code HIDDEN})에도 남는다
 */
public record CommentView(String id, String postId, String parentId, ReplyToView replyTo, PostAuthorView author, String content, String contentFormat, String state,
                          Instant createdAt, Instant updatedAt, int likeCount, boolean liked, boolean mine, String blocked, String stance) {

    /** 차단·입장 칸 없이(null) */
    public CommentView(String id, String postId, String parentId, ReplyToView replyTo, PostAuthorView author, String content, String contentFormat, String state,
                       Instant createdAt, Instant updatedAt, int likeCount, boolean liked, boolean mine) {
        this(id, postId, parentId, replyTo, author, content, contentFormat, state, createdAt, updatedAt, likeCount, liked, mine, null, null);
    }
}

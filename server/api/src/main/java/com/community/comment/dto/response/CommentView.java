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
 * @param stance    하이라이트 토론의 입장 배지 PRO · CON · NEUTRAL, 일반 댓글은 null — 의견으로 쓸 때 붙고, 글쓴이가 입장을 바꾸면(PUT …/stance) 그 글의 기존 의견도 새 입장으로
 *                  바뀐다. 답글·지운 자리({@code DELETED})·숨긴 자리({@code HIDDEN})에도 남는다
 * @param hiddenBy  숨긴 까닭을 밝힐 때만 {@code STAFF}(운영자가 숨김) — {@code state} 가 HIDDEN 이고 운영자 숨김이며 게시판 옵션 {@code staffHiddenNotice} 가 켜졌을 때. 그 밖(신고 숨김·옵션 끔·숨기지 않음)은 늘 null
 * @param pinned    운영자가 고정한 토론 의견이고 지금 보인다(지우거나 숨기지 않음) — 토론 의견 목록({@code kind} OPINION·PRO·CON·NEUTRAL)에서는 맨 위에 온다
 * @param dislikeCount 비추천 수 — 게시판 정책 {@code commentDislikeEnabled} 가 꺼져 있으면 null(쌓인 수는 보존하되 내리지 않는다)
 * @param disliked  내가 비추천했는가 — 비추천이 꺼진 게시판이면 null. 추천({@code liked})과 비추천은 동시에 참이 아니다
 * @param pinRequest 내 의견의 고정 요청 상태(대기·거절) — 내가 쓴 의견 원댓글이 아니거나 요청이 없거나 승인됐으면 null
 */
public record CommentView(String id, String postId, String parentId, ReplyToView replyTo, PostAuthorView author, String content, String contentFormat, String state,
                          Instant createdAt, Instant updatedAt, int likeCount, boolean liked, boolean mine, String blocked, String stance,
                          String hiddenBy, boolean pinned, Integer dislikeCount, Boolean disliked, PinRequestStateView pinRequest) {

    /** 차단·입장·숨김 까닭·고정·비추천 칸 없이(null·false) */
    public CommentView(String id, String postId, String parentId, ReplyToView replyTo, PostAuthorView author, String content, String contentFormat, String state,
                       Instant createdAt, Instant updatedAt, int likeCount, boolean liked, boolean mine) {
        this(id, postId, parentId, replyTo, author, content, contentFormat, state, createdAt, updatedAt, likeCount, liked, mine, null, null, null, false, null, null, null);
    }

    /** 고정 표시를 바꾼 사본 */
    public CommentView withPinned(boolean value) {
        return new CommentView(id, postId, parentId, replyTo, author, content, contentFormat, state, createdAt, updatedAt, likeCount, liked, mine, blocked, stance,
                hiddenBy, value, dislikeCount, disliked, pinRequest);
    }
}

package com.community.debate.dto.response;

import com.community.post.dto.response.PostAuthorView;

/**
 * @ 지정 후보 한 명 — 이 글에 댓글을 쓴 회원과, 그 회원에게 답글로 답할 대상.
 *
 * @param author    작성자 표시(보는 사람의 메모 {@code memo} 포함)
 * @param parentId  답글로 쓸 때 보낼 원댓글 ID({@code POST …/comments} 의 {@code parentId}) — 그 회원의 가장 최근 댓글이 원댓글이면 그 댓글, 답글이면 그 원댓글
 * @param replyToId 답글로 쓸 때 보낼 {@code replyToId} — 그 회원의 가장 최근(지우거나 숨기지 않은) 댓글
 */
public record CommenterView(PostAuthorView author, String parentId, String replyToId) {
}

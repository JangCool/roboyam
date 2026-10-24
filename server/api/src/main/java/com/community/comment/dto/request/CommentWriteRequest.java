package com.community.comment.dto.request;

import com.community.board.dto.request.GuestCredentials;

/**
 * {@code POST /api/posts/{id}/comments}(user-web {@code CommentWriteRequest}).
 *
 * @param contentFormat 본문 형식 TEXT · HTML — 빠지면 TEXT. 댓글 에디터가 NONE 인 게시판에 HTML 이면 400 {@code fields.contentFormat}
 * @param parentId  답글이면 원댓글 ID
 * @param replyToId 답글의 답글이면 그 댓글 ID(이름 표시용) — 원댓글이거나 같은 원댓글 아래의 답글이어야 한다
 * @param guest     비회원이면 필수
 * @param stance    하이라이트 토론의 입장 PRO · CON · NEUTRAL(선택, 대소문자·앞뒤 공백 무시). 보내면 <b>의견 표시</b>이고 내 지금 입장과 같아야 한다(다르면 409 STANCE_LOCKED) —
 *                  입장을 정하지는 않는다(입장은 {@code PUT …/stance} 투표로만). 회원만
 * @param opinion   {@code true} 면 <b>입장 의견</b>으로 쓴다(토론 구획) — 내 지금 입장이 배지로 붙는다. 표시({@code opinion}·{@code stance})가 없으면 입장을 가진 회원이 써도 일반 댓글.
 *                  답글은 이 칸과 무관하게 원댓글을 따른다(의견에 단 답글 = 의견, 일반 댓글에 단 답글 = 일반)
 *                  (docs/features/popular-highlight-contract.md 「서버 구현 결과」 — {@code DebateReader#resolveForWrite})
 */
public record CommentWriteRequest(String contentFormat, String content, String parentId, String replyToId, GuestCredentials guest, String stance,
                                  Boolean opinion) {
}

package com.community.comment.dto.request;

import com.community.board.dto.request.GuestCredentials;

/**
 * {@code POST /api/posts/{id}/comments}(user-web {@code CommentWriteRequest}).
 *
 * @param contentFormat 본문 형식 TEXT · HTML — 빠지면 TEXT. 댓글 에디터가 NONE 인 게시판에 HTML 이면 400 {@code fields.contentFormat}
 * @param parentId  답글이면 원댓글 ID
 * @param replyToId 답글의 답글이면 그 댓글 ID(이름 표시용) — 원댓글이거나 같은 원댓글 아래의 답글이어야 한다
 * @param guest     비회원이면 필수
 * @param stance    하이라이트 토론의 입장 PRO · CON(선택, 대소문자·앞뒤 공백 무시 — 비우면 일반 댓글이거나 서버가 내 현재 입장을 붙인다). 회원만, 토론이 열린 글만
 *                  (docs/features/popular-highlight-contract.md 「서버 구현 결과」 — {@code DebateReader#resolveForWrite})
 */
public record CommentWriteRequest(String contentFormat, String content, String parentId, String replyToId, GuestCredentials guest, String stance) {
}

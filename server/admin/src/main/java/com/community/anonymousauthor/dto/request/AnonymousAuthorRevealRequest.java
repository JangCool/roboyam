package com.community.anonymousauthor.dto.request;

/**
 * {@code POST /api/admin/anonymous-authors/reveal} — 검사는 서비스가 한다(anonymous-board-contract 정책 10).
 *
 * @param postId    익명 글(댓글이면 비운다) — {@code commentId} 와 둘 중 하나만
 * @param commentId 익명 댓글
 * @param reason    LEGAL_REQUEST · RIGHTS_REPORT · ABUSE · OTHER(필수 — 모르면 400 {@code fields.reason})
 * @param memo      메모 1~500자(필수 — 400 {@code fields.memo}). 감사 상세에만 남는다
 */
public record AnonymousAuthorRevealRequest(String postId, String commentId, String reason, String memo) {

    @Override
    public String toString() {
        return "AnonymousAuthorRevealRequest[postId=" + postId + ", commentId=" + commentId + ", reason=" + reason + "]";
    }
}

package com.community.sanction.dto.response;

import com.community.sanction.repository.master.BoardSanctionStore.SealedSource;

/**
 * 봉인 검토(V184 — 줄을 이루는 제재 중 하나라도 「이 글/댓글 작성자」 제재)의 제재 대상 — 그 줄의 봉인 제재 가운데 가장 최근 것의 계기 글 또는 댓글. 둘 중 <b>하나만</b> 있다
 * (댓글이면 {@code commentId} 만 — 화면은 그대로 {@code anonymousPostId}/{@code anonymousCommentId} 로 {@code POST /api/admin/sanctions}·{@code GET …/preview} 를 부른다).
 * 계기를 잃은 봉인 제재뿐이면 둘 다 null(그 줄에서는 사이트 제재를 걸 수 없다).
 */
public record BoardSanctionReviewSealedSourceView(String postId, String commentId) {

    public static BoardSanctionReviewSealedSourceView of(SealedSource s) {
        if (s.commentId() != null) {
            return new BoardSanctionReviewSealedSourceView(null, Long.toString(s.commentId()));
        }
        return new BoardSanctionReviewSealedSourceView(s.postId() == null ? null : Long.toString(s.postId()), null);
    }
}

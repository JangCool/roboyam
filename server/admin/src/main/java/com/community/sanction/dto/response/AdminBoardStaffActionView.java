package com.community.sanction.dto.response;

import java.time.Instant;

/**
 * 스텝 처리 한 줄(admin-web {@code AdminBoardStaffAction}, board-staff-action-contract 「API > 관리자」) — 목록·상세·되돌리기 응답이 같은 모양. 처리자 신원은 관리자 응답에만(정책 8).
 *
 * @param kind       POST_HIDE · POST_DELETE · COMMENT_DELETE
 * @param reasonCode SPAM · ABUSE · INVESTMENT · OFF_TOPIC
 * @param restored   되돌렸으면 시각 · 되돌린 사람(MANAGER 그 게시판 매니저 닉네임 · OPERATOR 운영자 이름) · 사유, 아니면 null
 */
public record AdminBoardStaffActionView(String id, BoardSanctionBoardRefView board, String kind, String reasonCode, Actor actor, Target target, Instant createdAt,
                                        Restored restored) {

    /**
     * 처리한 매니저·스텝(회원) — 닉네임은 지금 값(없으면 null), 자리는 처리 때. 운영자 회원 계정이면 {@code role} OPERATOR(닉네임 = 그 회원 닉네임) +
     * {@code operatorName}(운영자의 지금 이름 — operator-member-account-contract), 그 밖 null
     */
    public record Actor(String id, String nickname, String role, String operatorName) {
    }

    /**
     * 처리 대상 — 제목·댓글 앞 40자는 처리 때 스냅숏.
     *
     * @param state 댓글 삭제 줄이면 댓글의, 글 줄이면 글의 지금 상태(PUBLIC · HIDDEN · DELETED)
     */
    public record Target(String postId, String commentId, String title, String excerpt, Author author, String state) {
    }

    /** 대상 작성자 — 회원(지금 닉네임, 없으면 스냅숏) 또는 비회원(이름 스냅숏). 익명 글·댓글(V184)이면 {@code kind} SEALED · ID·이름 null(봉인) */
    public record Author(String kind, String id, String name) {
    }

    public record Restored(Instant at, By by, String reason) {
    }

    public record By(String kind, String name) {
    }
}

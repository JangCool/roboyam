package com.community.boardstaff.dto.response;

import java.time.Instant;

/**
 * 처리 내역 한 줄(user-web {@code BoardStaffActionRow}) — 처리자({@code actor})는 그 게시판 매니저에게만(스텝이 보면 null). 되돌린 사람·사유는 싣지 않는다.
 *
 * @param kind       POST_HIDE · POST_DELETE · COMMENT_DELETE
 * @param canRestore 지금 보는 사람이 되돌릴 수 있다(매니저 + {@code board.staff-action.restore} · 아직 되돌리지 않음)
 */
public record BoardStaffActionRowView(String id, String kind, String reasonCode, Target target, Author targetAuthor, Actor actor, Instant createdAt, Restored restored,
                                      boolean canRestore) {

    /** 대상 — 제목·댓글 앞 40자는 처리 때 스냅숏 */
    public record Target(String postId, String commentId, String title, String excerpt) {
    }

    /** 대상 작성자 — 회원 닉네임 또는 비회원 이름 */
    public record Author(String nickname) {
    }

    /** 처리자 닉네임 · 자리(MANAGER · STAFF) */
    public record Actor(String nickname, String role) {
    }

    public record Restored(Instant at) {
    }
}

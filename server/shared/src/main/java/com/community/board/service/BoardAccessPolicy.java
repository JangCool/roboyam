package com.community.board.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.permission.entity.MemberPermission;

/**
 * 공개 게시판의 글쓰기·댓글 판정 — DB 를 읽지 않는 순수 함수다. 게시판 응답의 {@code viewer.writeBlockedReason}·{@code commentBlockedReason} 과 쓰기 API 의 거절이
 * 모두 여기를 지난다(같은 규칙이 여러 곳에 복사되지 않게). 돌려주는 값은 user-web {@code WriteBlockedReason} 이고 null 이면 쓸 수 있다.
 *
 * <p><b>판정 순서</b>(첫 번째 까닭을 돌려준다 — user-web 목업 {@code mockWriteBlocked}·{@code mockCommentBlocked} 와 community {@code docs/api-board-settings.md} 2-1 을 합친 것):
 * <ol>
 *   <li>(댓글만) 유효 {@code commentMin = OFF} → {@code COMMENTS_OFF}</li>
 *   <li>읽기 전용 잠금(글: POST·POST_COMMENT, 댓글: POST_COMMENT) — 이 게시판의 매니저·스텝은 면제 → {@code WRITE_LOCKED}</li>
 *   <li>최소 자격 {@code STAFF} 인데 이 게시판의 매니저·스텝이 아님 → {@code STAFF_ONLY}</li>
 *   <li>비회원: 최소 자격이 {@code GUEST} 면 통과, 아니면 {@code LOGIN_REQUIRED}</li>
 *   <li>정지·영구 정지 → {@code ACCOUNT_RESTRICTED}</li>
 *   <li>이메일 인증 전(최소 자격이 GUEST 가 아닐 때만 — 비회원도 쓰는 게시판에서 회원만 막지 않는다) → {@code EMAIL_NOT_VERIFIED}</li>
 *   <li>사업자 회원 → {@code BUSINESS_ACCOUNT_RESTRICTED}(일반 게시판 글쓰기 불가)</li>
 *   <li>회원 역할 권한({@code post.write}·{@code comment.write})이 없음 → {@code BOARD_RESTRICTED} — 역할 권한과 게시판 규칙을 <b>둘 다</b> 만족해야 한다(docs/domain/operator-permissions.md 「회원 역할」)</li>
 *   <li>최소 자격이 등급인데 회원 등급의 서열이 모자람 → {@code GRADE_TOO_LOW}. 매니저·스텝은 등급을 면제받는다. 등급 목록에 없는 키(지워진 등급)는 첫 등급처럼 본다</li>
 * </ol>
 * 꺼진 게시판은 부르는 쪽이 먼저 {@code 403 BOARD_INACTIVE} 로 막는다.
 */
public final class BoardAccessPolicy {

    public static final String LOGIN_REQUIRED = "LOGIN_REQUIRED";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String STAFF_ONLY = "STAFF_ONLY";
    public static final String WRITE_LOCKED = "WRITE_LOCKED";
    public static final String GRADE_TOO_LOW = "GRADE_TOO_LOW";
    public static final String BOARD_RESTRICTED = "BOARD_RESTRICTED";
    public static final String COMMENTS_OFF = "COMMENTS_OFF";
    public static final String BUSINESS_ACCOUNT_RESTRICTED = "BUSINESS_ACCOUNT_RESTRICTED";

    private BoardAccessPolicy() {
    }

    /** 새 글을 쓸 수 없는 까닭(쓸 수 있으면 null) */
    public static String writeBlocked(EffectiveBoard board, BoardActor actor, BoardCatalogSnapshot catalog) {
        return decide(board, actor, catalog, board.writeMin(), board.postLocked(), MemberPermission.POST_WRITE.code());
    }

    /** 새 댓글을 쓸 수 없는 까닭(쓸 수 있으면 null) */
    public static String commentBlocked(EffectiveBoard board, BoardActor actor, BoardCatalogSnapshot catalog) {
        if (BoardFields.OFF.equals(board.commentMin())) {
            return COMMENTS_OFF;
        }
        return decide(board, actor, catalog, board.commentMin(), board.commentLocked(), MemberPermission.COMMENT_WRITE.code());
    }

    private static String decide(EffectiveBoard board, BoardActor actor, BoardCatalogSnapshot catalog, String min, boolean locked, String permission) {
        boolean staff = actor.staffOf(board.id());
        if (locked && !staff) {
            return WRITE_LOCKED;
        }
        if (BoardFields.STAFF.equals(min) && !staff) {
            return STAFF_ONLY;
        }
        boolean guestMin = BoardFields.GUEST.equals(min);
        if (actor.guest()) {
            return guestMin ? null : LOGIN_REQUIRED;
        }
        if (actor.restricted()) {
            return ACCOUNT_RESTRICTED;
        }
        if (!guestMin && !actor.emailVerified()) {
            return EMAIL_NOT_VERIFIED;
        }
        if (actor.business()) {
            return BUSINESS_ACCOUNT_RESTRICTED;
        }
        if (!actor.can(permission)) {
            return BOARD_RESTRICTED;
        }
        if (staff || guestMin || BoardFields.STAFF.equals(min)) {
            return null;
        }
        Integer required = catalog.gradeLevel(min);
        if (required == null) {
            return null; // 지워진 등급 키 — 첫 등급처럼(누구나) 본다
        }
        return actor.gradeLevel() >= required ? null : GRADE_TOO_LOW;
    }

    /** 이 회원에게 「새 회원」(첫 등급) 링크·이미지 규칙이 걸리는가 — 매니저·스텝은 면제 */
    public static boolean newMemberRules(EffectiveBoard board, BoardActor actor, BoardCatalogSnapshot catalog) {
        return !actor.guest() && !actor.staffOf(board.id()) && actor.gradeKey() != null && actor.gradeKey().equals(catalog.firstGradeKey());
    }
}

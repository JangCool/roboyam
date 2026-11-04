package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * 게시판 스텝 처리(docs/features/board-staff-action-contract.md)의 거절 — 코드마다 정적 팩터리 하나. 403 {@code BOARD_STAFF_FORBIDDEN} · 404 {@code BOARD_STAFF_NOT_FOUND} 는
 * {@link BoardSanctionException} 을 같이 쓴다. 글 상태 409 {@code POST_STATE_INVALID}·{@code POST_PURGED} 는 운영자 조치와 같은 예외(shared {@code com.community.post.exception}).
 * <b>누가 스텝인지 · 처리자 신원을 문구에 싣지 않는다</b>(정책 4·8 — 처리할 수 없는 대상은 「처리할 수 없는 글·댓글이에요」 하나).
 */
public class BoardStaffActionException extends CommonException {

    public static final String POST_NOT_FOUND = "POST_NOT_FOUND";
    public static final String COMMENT_NOT_FOUND = "COMMENT_NOT_FOUND";
    public static final String COMMENT_STATE_INVALID = "COMMENT_STATE_INVALID";
    public static final String ACTION_NOT_FOUND = "BOARD_STAFF_ACTION_NOT_FOUND";
    public static final String ACTION_RESTORED = "BOARD_STAFF_ACTION_RESTORED";

    private BoardStaffActionException(HttpStatus status, String code, MessageRef message) {
        super(status, code, message);
    }

    private static BoardStaffActionException of(HttpStatus status, String code) {
        return new BoardStaffActionException(status, code, new MessageRef("error." + code));
    }

    /** 404 — 없는 글(임시저장 포함) */
    public static BoardStaffActionException postNotFound() {
        return of(HttpStatus.NOT_FOUND, POST_NOT_FOUND);
    }

    /** 404 — 없는 댓글 · 그 글의 댓글이 아님 */
    public static BoardStaffActionException commentNotFound() {
        return of(HttpStatus.NOT_FOUND, COMMENT_NOT_FOUND);
    }

    /** 409 — 댓글 상태가 그 처리를 허락하지 않는다(이미 지움 · 글이 지워짐 · 되돌리려는데 지워져 있지 않음 · 파기) */
    public static BoardStaffActionException commentStateInvalid() {
        return of(HttpStatus.CONFLICT, COMMENT_STATE_INVALID);
    }

    /** 404 — 없는 처리 기록(제재 기록 포함 — 이 목록에 없다) */
    public static BoardStaffActionException actionNotFound() {
        return of(HttpStatus.NOT_FOUND, ACTION_NOT_FOUND);
    }

    /** 409 — 이미 되돌린 처리(조건부 UPDATE 로 한 번만) */
    public static BoardStaffActionException alreadyRestored() {
        return of(HttpStatus.CONFLICT, ACTION_RESTORED);
    }

    /** 409 {@code BOARD_STAFF_TARGET_PROTECTED} — 공지·고정 글 · 같은 게시판 매니저·스텝의 글·댓글 · 소모임 글 · 본인 것(문구 「처리할 수 없는 글·댓글이에요」) */
    public static BoardStaffActionException targetProtected() {
        return new BoardStaffActionException(HttpStatus.CONFLICT, BoardSanctionException.TARGET_PROTECTED, new MessageRef("boardStaffAction.target.protected"));
    }

    /** 409 {@code BOARD_STAFF_DAILY_LIMIT} — 오늘 그 종류 한도를 다 썼다. 본문 최상위 {@code kind}(POST · COMMENT)·{@code limit}·{@code used}·{@code resetsAt} */
    public static BoardStaffActionException dailyLimit(String kind, int limit, int used, Instant resetsAt) {
        BoardStaffActionException e = new BoardStaffActionException(HttpStatus.CONFLICT, BoardSanctionException.DAILY_LIMIT, new MessageRef("boardStaffAction.dailyLimit." + kind));
        e.extra("kind", kind);
        e.extra("limit", limit);
        e.extra("used", used);
        e.extra("resetsAt", resetsAt);
        return e;
    }
}

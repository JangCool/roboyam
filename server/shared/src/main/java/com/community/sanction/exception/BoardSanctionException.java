package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * 게시판 제재(docs/features/board-sanction-contract.md)의 거절 — 코드마다 정적 팩터리 하나. 문구 키는 {@code error.<코드>}.
 * <b>처리자 신원·누가 스텝인지를 문구에 싣지 않는다</b>(결정 7 — 받을 수 없는 대상은 「제재할 수 없는 회원이에요」 하나).
 */
public class BoardSanctionException extends CommonException {

    public static final String STAFF_FORBIDDEN = "BOARD_STAFF_FORBIDDEN";
    public static final String STAFF_NOT_FOUND = "BOARD_STAFF_NOT_FOUND";
    public static final String TARGET_PROTECTED = "BOARD_STAFF_TARGET_PROTECTED";
    public static final String ALREADY_RESTRICTED = "BOARD_STAFF_ALREADY_RESTRICTED";
    public static final String DAILY_LIMIT = "BOARD_STAFF_DAILY_LIMIT";
    public static final String NOT_FOUND = "BOARD_SANCTION_NOT_FOUND";
    public static final String NOT_LIFTABLE = "BOARD_SANCTION_NOT_LIFTABLE";
    public static final String ALREADY_VOIDED = "BOARD_SANCTION_ALREADY_VOIDED";
    public static final String REVIEW_NOT_FOUND = "BOARD_SANCTION_REVIEW_NOT_FOUND";
    public static final String REVIEW_CLOSED = "BOARD_SANCTION_REVIEW_CLOSED";

    private BoardSanctionException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    /** 403 — 그 게시판의 매니저·스텝이 아니거나 역할 권한({@code board.sanction.issue}·{@code board.staff-action.restore})이 없거나 계정이 정상·인증 상태가 아니다 */
    public static BoardSanctionException forbidden() {
        return new BoardSanctionException(HttpStatus.FORBIDDEN, STAFF_FORBIDDEN);
    }

    /** 404 — 매니저·스텝이 아닌 사람이 제재 내역을 열었다(화면은 404 안내) */
    public static BoardSanctionException staffNotFound() {
        return new BoardSanctionException(HttpStatus.NOT_FOUND, STAFF_NOT_FOUND);
    }

    /** 409 — 받을 수 없는 대상(본인 · 같은 게시판 매니저·스텝 · 비회원 · 탈퇴 · 없는 회원) */
    public static BoardSanctionException targetProtected() {
        return new BoardSanctionException(HttpStatus.CONFLICT, TARGET_PROTECTED);
    }

    /** 409 — 이 게시판에서 진행 중인 쓰기 제한이 있어 쓰기 제한은 더 못 준다(경고만) */
    public static BoardSanctionException alreadyRestricted() {
        return new BoardSanctionException(HttpStatus.CONFLICT, ALREADY_RESTRICTED);
    }

    /** 409 — 오늘 한도를 다 썼다. 본문 최상위 {@code limit}·{@code used}·{@code resetsAt}(다음 한국 시간 0시) */
    public static BoardSanctionException dailyLimit(int limit, int used, Instant resetsAt) {
        BoardSanctionException e = new BoardSanctionException(HttpStatus.CONFLICT, DAILY_LIMIT);
        e.extra("limit", limit);
        e.extra("used", used);
        e.extra("resetsAt", resetsAt);
        return e;
    }

    /** 404 — 없는 게시판 제재(회원 쪽은 내 것이 아닌 것도, 매니저 쪽은 다른 게시판 것도) */
    public static BoardSanctionException notFound() {
        return new BoardSanctionException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    /** 409 — 진행 중인 쓰기 제한만 해제할 수 있다(경고 · 이미 끝남 · 해제 · 무효) */
    public static BoardSanctionException notLiftable() {
        return new BoardSanctionException(HttpStatus.CONFLICT, NOT_LIFTABLE);
    }

    /** 409 — 이미 무효 */
    public static BoardSanctionException alreadyVoided() {
        return new BoardSanctionException(HttpStatus.CONFLICT, ALREADY_VOIDED);
    }

    /** 404 — 없는 반복 검토 */
    public static BoardSanctionException reviewNotFound() {
        return new BoardSanctionException(HttpStatus.NOT_FOUND, REVIEW_NOT_FOUND);
    }

    /** 409 — 이미 닫힌 반복 검토(행 잠금으로 한 번만) */
    public static BoardSanctionException reviewClosed() {
        return new BoardSanctionException(HttpStatus.CONFLICT, REVIEW_CLOSED);
    }
}

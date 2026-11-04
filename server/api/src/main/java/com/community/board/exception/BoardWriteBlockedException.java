package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 이 게시판에 글·댓글을 쓸 수 없음(403) — 코드는 판정 까닭 그대로다(user-web {@code WriteBlockedReason}: {@code WRITE_LOCKED}·{@code STAFF_ONLY}·{@code GRADE_TOO_LOW}·
 * {@code EMAIL_NOT_VERIFIED}·{@code ACCOUNT_RESTRICTED}·{@code BUSINESS_ACCOUNT_RESTRICTED}·{@code BOARD_RESTRICTED}·{@code COMMENTS_OFF}). 로그인이 필요한 경우는
 * 이 예외가 아니라 {@code 401 UNAUTHENTICATED} 다. 문구 키는 {@code error.<까닭>}.
 */
public class BoardWriteBlockedException extends CommonException {

    public BoardWriteBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("error." + reason));
    }

    private BoardWriteBlockedException(String reason, MessageRef message) {
        super(HttpStatus.FORBIDDEN, reason, message);
    }

    /** 끝 시각 표시 형식(한국 시간) */
    private static final java.time.format.DateTimeFormatter UNTIL = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 이 게시판에서 쓰기 제한 중({@code BOARD_WRITE_RESTRICTED} — docs/features/board-sanction-contract.md 「쓰기 막힘 오류」). 문구 「이 게시판에서는 {날짜}까지 글·댓글을 쓸 수 없어요.」
     * (한국 시간), 본문 최상위 {@code until}(UTC ISO)·{@code boardSanctionId}·{@code reasonCode}. 처리자는 싣지 않는다(결정 7).
     */
    public static BoardWriteBlockedException boardRestricted(com.community.board.dto.BoardWriteRestriction restriction) {
        String until = UNTIL.format(restriction.until().atZone(com.community.zconfiguration.time.ServiceTime.ZONE));
        BoardWriteBlockedException e = new BoardWriteBlockedException(com.community.board.service.BoardAccessPolicy.BOARD_WRITE_RESTRICTED,
                new MessageRef("error.BOARD_WRITE_RESTRICTED.until", until));
        e.extra("until", restriction.until());
        e.extra("boardSanctionId", Long.toString(restriction.boardSanctionId()));
        e.extra("reasonCode", restriction.reasonCode());
        return e;
    }

    /**
     * 글쓰기 {@code GRADE_TOO_LOW} — 오류 본문 최상위 {@code minGrade}(등급 키)·{@code minGradeLabel}(등급 이름). 소모임 개설 {@code GROUP_GRADE_TOO_LOW} 의 {@code minGrade} 와 같은 이름이다.
     * 등급을 정할 수 없으면 칸 없이.
     */
    public static BoardWriteBlockedException gradeTooLow(String reason, String minGradeKey, String minGradeLabel) {
        BoardWriteBlockedException e = new BoardWriteBlockedException(reason);
        if (minGradeKey != null) {
            e.extra("minGrade", minGradeKey);
            e.extra("minGradeLabel", minGradeLabel == null ? minGradeKey : minGradeLabel);
        }
        return e;
    }
}

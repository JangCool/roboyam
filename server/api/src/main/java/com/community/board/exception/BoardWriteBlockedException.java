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

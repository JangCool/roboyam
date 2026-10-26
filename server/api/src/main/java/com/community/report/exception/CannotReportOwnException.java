package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내 글·댓글·내가 모임장인 소모임은 신고할 수 없음(400) — 코드는 하나, 문구만 대상에 맞춘다 */
public class CannotReportOwnException extends CommonException {

    public CannotReportOwnException() {
        this("error.CANNOT_REPORT_OWN");
    }

    private CannotReportOwnException(String messageKey) {
        super(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN", new MessageRef(messageKey));
    }

    /** 내가 모임장인 소모임(V95) */
    public static CannotReportOwnException group() {
        return new CannotReportOwnException("error.CANNOT_REPORT_OWN.group");
    }
}

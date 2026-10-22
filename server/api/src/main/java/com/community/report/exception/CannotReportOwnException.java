package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내 글·댓글은 신고할 수 없음(400) */
public class CannotReportOwnException extends CommonException {

    public CannotReportOwnException() {
        super(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN", new MessageRef("error.CANNOT_REPORT_OWN"));
    }
}

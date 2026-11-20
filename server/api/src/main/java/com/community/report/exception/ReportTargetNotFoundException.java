package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고할 글·댓글이 없거나 지워졌거나 이미 숨겨짐(404) */
public class ReportTargetNotFoundException extends CommonException {

    public ReportTargetNotFoundException() {
        this("error.REPORT_TARGET_NOT_FOUND");
    }

    private ReportTargetNotFoundException(String messageKey) {
        super(HttpStatus.NOT_FOUND, "REPORT_TARGET_NOT_FOUND", new MessageRef(messageKey));
    }

    /** 짤 신고 — 없는 짤·내려간 짤(코드는 같고 문구만 짤용) */
    public static ReportTargetNotFoundException jjal() {
        return new ReportTargetNotFoundException("error.REPORT_TARGET_NOT_FOUND.jjal");
    }
}

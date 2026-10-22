package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고 기능이 꺼져 있음(403, 관리자 신고 정책 reportEnabled) */
public class ReportDisabledException extends CommonException {

    public ReportDisabledException() {
        super(HttpStatus.FORBIDDEN, "REPORT_DISABLED", new MessageRef("error.REPORT_DISABLED"));
    }
}

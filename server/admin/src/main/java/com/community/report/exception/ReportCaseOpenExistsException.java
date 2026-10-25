package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고 관리 — 다시 열려는 사건의 대상에 이미 새 열린 사건이 있다(409 REPORT_CASE_OPEN_EXISTS) */
public class ReportCaseOpenExistsException extends CommonException {

    public ReportCaseOpenExistsException() {
        super(HttpStatus.CONFLICT, "REPORT_CASE_OPEN_EXISTS", new MessageRef("error.REPORT_CASE_OPEN_EXISTS"));
    }
}

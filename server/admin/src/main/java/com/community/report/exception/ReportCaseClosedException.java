package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고 관리 — 이미 닫힌 사건을 조치·무시, 조치한 사건을 다시 열기(409 REPORT_CASE_CLOSED) */
public class ReportCaseClosedException extends CommonException {

    public static final String CODE = "REPORT_CASE_CLOSED";

    public ReportCaseClosedException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error." + CODE));
    }
}

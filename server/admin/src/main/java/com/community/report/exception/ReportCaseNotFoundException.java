package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고 관리 — 없는 사건·형식이 틀린 번호(404 REPORT_CASE_NOT_FOUND) */
public class ReportCaseNotFoundException extends CommonException {

    public static final String CODE = "REPORT_CASE_NOT_FOUND";

    public ReportCaseNotFoundException() {
        super(HttpStatus.NOT_FOUND, CODE, new MessageRef("error." + CODE));
    }
}

package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 대상을 이미 신고함(409) */
public class AlreadyReportedException extends CommonException {

    public AlreadyReportedException() {
        super(HttpStatus.CONFLICT, "ALREADY_REPORTED", new MessageRef("error.ALREADY_REPORTED"));
    }
}

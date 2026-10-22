package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고할 글·댓글이 없거나 지워졌거나 이미 숨겨짐(404) */
public class ReportTargetNotFoundException extends CommonException {

    public ReportTargetNotFoundException() {
        super(HttpStatus.NOT_FOUND, "REPORT_TARGET_NOT_FOUND", new MessageRef("error.REPORT_TARGET_NOT_FOUND"));
    }
}

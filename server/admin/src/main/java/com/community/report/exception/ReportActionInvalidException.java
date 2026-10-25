package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고 관리 — 대상의 지금 상태가 그 조치를 허락하지 않는다(409 REPORT_ACTION_INVALID). 예: 숨긴 글을 숨기기 · 자동 숨김이 아닌 대상의 자동 숨김 확정 · 지운 대상 삭제 */
public class ReportActionInvalidException extends CommonException {

    public ReportActionInvalidException() {
        super(HttpStatus.CONFLICT, "REPORT_ACTION_INVALID", new MessageRef("error.REPORT_ACTION_INVALID"));
    }
}

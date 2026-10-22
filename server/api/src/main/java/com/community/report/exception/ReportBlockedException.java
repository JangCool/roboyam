package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 반려 누적으로 신고가 막힘(403) — 막힘이 풀릴 때까지 남은 초를 함께 준다 */
public class ReportBlockedException extends CommonException {

    public ReportBlockedException(long retryAfterSeconds) {
        super(HttpStatus.FORBIDDEN, "REPORT_BLOCKED", new MessageRef("error.REPORT_BLOCKED"), Map.of(), retryAfterSeconds);
    }
}

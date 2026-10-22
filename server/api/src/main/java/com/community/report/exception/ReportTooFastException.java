package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 신고 간격(신고 정책 reportCooldownSeconds) 안에 다시 신고함(429) */
public class ReportTooFastException extends CommonException {

    public ReportTooFastException(int gapSeconds, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "REPORT_TOO_FAST", new MessageRef("error.REPORT_TOO_FAST", gapSeconds), Map.of(), retryAfterSeconds);
    }
}

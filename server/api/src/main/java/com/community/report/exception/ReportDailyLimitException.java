package com.community.report.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 하루 신고 수(한국시간 0시 기준, 신고 정책 dailyReportLimit)를 넘음(429) */
public class ReportDailyLimitException extends CommonException {

    public ReportDailyLimitException(int limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "REPORT_DAILY_LIMIT", new MessageRef("error.REPORT_DAILY_LIMIT", String.valueOf(limit)));
    }
}

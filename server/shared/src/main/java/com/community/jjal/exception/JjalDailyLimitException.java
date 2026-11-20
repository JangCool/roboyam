package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 429 — 최근 24시간 올리기 한도(기본 50장). {@code retryAfterSeconds} */
public class JjalDailyLimitException extends CommonException {

    public JjalDailyLimitException(int perDay, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "JJAL_DAILY_LIMIT", new MessageRef("error.JJAL_DAILY_LIMIT", perDay), Map.of(), retryAfterSeconds);
    }
}

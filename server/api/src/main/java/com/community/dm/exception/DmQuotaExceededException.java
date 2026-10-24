package com.community.dm.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 오늘 한도를 다 씀(429 {@code DM_DAILY_LIMIT} · {@code DM_NEW_PARTNER_LIMIT}) — {@code retryAfterSeconds} 는 한국 시간 0시까지, 문구에 한도 */
public class DmQuotaExceededException extends CommonException {

    private DmQuotaExceededException(String code, int limit, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, new MessageRef("error." + code, String.valueOf(limit)), Map.of(), Math.max(1, retryAfterSeconds));
    }

    public static DmQuotaExceededException daily(int limit, long retryAfterSeconds) {
        return new DmQuotaExceededException("DM_DAILY_LIMIT", limit, retryAfterSeconds);
    }

    public static DmQuotaExceededException newPartners(int limit, long retryAfterSeconds) {
        return new DmQuotaExceededException("DM_NEW_PARTNER_LIMIT", limit, retryAfterSeconds);
    }
}

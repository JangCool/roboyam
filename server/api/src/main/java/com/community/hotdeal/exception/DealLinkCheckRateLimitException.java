package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 링크 판정(OG 가져오기)을 회원이 1분에 너무 많이 불렀다(429, 서버 규칙 1 — 분당 10회) */
public class DealLinkCheckRateLimitException extends CommonException {

    public DealLinkCheckRateLimitException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "DEAL_LINK_CHECK_RATE_LIMIT", new MessageRef("error.DEAL_LINK_CHECK_RATE_LIMIT"), Map.of(), retryAfterSeconds);
    }
}
package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 회원 하루(한국 0시 기준) 종료 요청 수({@code endRequestDailyLimit})에 닿았다(429) — 오류 본문 최상위 {@code limit} */
public class DealEndRequestDailyLimitException extends CommonException {

    public DealEndRequestDailyLimitException(int limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "DEAL_END_REQUEST_DAILY_LIMIT", new MessageRef("error.DEAL_END_REQUEST_DAILY_LIMIT", String.valueOf(limit)));
        extra("limit", limit);
    }
}

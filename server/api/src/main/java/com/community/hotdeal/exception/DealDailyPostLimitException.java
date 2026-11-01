package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 회원 하루(한국 0시 기준) 핫딜 글 수 한도({@code memberDailyPostLimit})에 닿았다(429) — 오류 본문 최상위 {@code limit} */
public class DealDailyPostLimitException extends CommonException {

    public DealDailyPostLimitException(int limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "DEAL_DAILY_POST_LIMIT", new MessageRef("error.DEAL_DAILY_POST_LIMIT", String.valueOf(limit)));
        extra("limit", limit);
    }
}
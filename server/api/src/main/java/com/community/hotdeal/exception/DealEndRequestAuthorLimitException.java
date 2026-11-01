package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 작성자의 글에 그 기간({@code endRequestPerAuthorDays}) 보낼 수 있는 요청 수({@code endRequestPerAuthorLimit})에 닿았다(429) — {@code extras} {@code limit}·{@code days} */
public class DealEndRequestAuthorLimitException extends CommonException {

    public DealEndRequestAuthorLimitException(int limit, int days) {
        super(HttpStatus.TOO_MANY_REQUESTS, "DEAL_END_REQUEST_AUTHOR_LIMIT", new MessageRef("error.DEAL_END_REQUEST_AUTHOR_LIMIT", String.valueOf(days), String.valueOf(limit)));
        extra("limit", limit);
        extra("days", days);
    }
}

package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 같은 판매처 연속 글 간격({@code sameStoreIntervalMinutes})이 아직 지나지 않았다(429) — 오류 본문 최상위 {@code remainingMinutes}·{@code intervalMinutes} +
 * {@code retryAfterSeconds}(헤더 {@code Retry-After} 도).
 */
public class DealSameStoreTooSoonException extends CommonException {

    public DealSameStoreTooSoonException(long remainingMinutes, int intervalMinutes, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "DEAL_SAME_STORE_TOO_SOON",
                new MessageRef("error.DEAL_SAME_STORE_TOO_SOON", String.valueOf(intervalMinutes), String.valueOf(remainingMinutes)), Map.of(), retryAfterSeconds);
        extra("remainingMinutes", remainingMinutes);
        extra("intervalMinutes", intervalMinutes);
    }
}
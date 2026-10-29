package com.community.address.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 IP 가 주소 검색을 너무 자주 불렀다(429) — {@code AddressSearchRateLimiter} */
public class AddressSearchRateLimitedException extends CommonException {

    public AddressSearchRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "ADDRESS_SEARCH_RATE_LIMITED", new MessageRef("error.ADDRESS_SEARCH_RATE_LIMITED"), Map.of(), retryAfterSeconds);
    }
}

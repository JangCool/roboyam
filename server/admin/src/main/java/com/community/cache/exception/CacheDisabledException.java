package com.community.cache.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 꺼진 캐시(전체 또는 이 캐시)는 비울 항목이 없어 「지금 갱신」을 받지 않는다(409). */
public class CacheDisabledException extends CommonException {

    public CacheDisabledException() {
        super(HttpStatus.CONFLICT, "CACHE_DISABLED", new MessageRef("error.CACHE_DISABLED"));
    }
}

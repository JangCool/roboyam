package com.community.cache.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 전체 캐시가 꺼져 있어 캐시별 켜기/끄기를 바꿀 수 없다(409). */
public class CacheGlobalDisabledException extends CommonException {

    public CacheGlobalDisabledException() {
        super(HttpStatus.CONFLICT, "CACHE_GLOBAL_DISABLED", new MessageRef("error.CACHE_GLOBAL_DISABLED"));
    }
}

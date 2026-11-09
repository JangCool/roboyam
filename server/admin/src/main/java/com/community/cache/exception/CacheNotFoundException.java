package com.community.cache.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 카탈로그에 없는 캐시다(404). */
public class CacheNotFoundException extends CommonException {

    public CacheNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CACHE_NOT_FOUND", new MessageRef("error.CACHE_NOT_FOUND"));
    }
}

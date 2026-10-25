package com.community.search.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 검색 한 번이 시간 제한(3초)을 넘겨 중단됐다(503) — 화면은 「검색어를 더 구체적으로」 */
public class SearchTimeoutException extends CommonException {

    public SearchTimeoutException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_TIMEOUT", new MessageRef("error.SEARCH_TIMEOUT"));
    }
}

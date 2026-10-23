package com.community.media.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 최근 24시간에 올린 글 이미지가 하루 한도에 닿았다(429) — 문구에 한도 수를 싣는다 */
public class ImageDailyLimitException extends CommonException {

    public ImageDailyLimitException(int limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "IMAGE_DAILY_LIMIT", new MessageRef("error.IMAGE_DAILY_LIMIT", String.valueOf(limit)));
    }
}

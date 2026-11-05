package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 429 — 작가가 최근 24시간에 올린 로보티콘 그림이 하루 한도를 넘었다(저장소 남용 방지 — 팩 하나 최대 40개 + 대표를 여러 번 고쳐 올려도 넉넉한 값) */
public class EmoticonUploadLimitException extends CommonException {

    public EmoticonUploadLimitException(int limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "EMOTICON_UPLOAD_LIMIT", new MessageRef("error.EMOTICON_UPLOAD_LIMIT", limit));
    }
}

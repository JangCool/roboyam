package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 로보티콘 팩이 없다(형식이 틀린 ID · 상점에 나오지 않은 남의 팩 · 스튜디오의 남의 팩 포함) */
public class EmoticonPackNotFoundException extends CommonException {

    public EmoticonPackNotFoundException() {
        super(HttpStatus.NOT_FOUND, "EMOTICON_PACK_NOT_FOUND", new MessageRef("error.EMOTICON_PACK_NOT_FOUND"));
    }
}

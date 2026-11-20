package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 짤이 없다(없는 공개 ID · 내 보관함에 없음 · 담을 글·댓글에 보이지 않음 — 있는지 알리지 않게 한 코드로) */
public class JjalNotFoundException extends CommonException {

    public JjalNotFoundException() {
        super(HttpStatus.NOT_FOUND, "JJAL_NOT_FOUND", new MessageRef("error.JJAL_NOT_FOUND"));
    }
}

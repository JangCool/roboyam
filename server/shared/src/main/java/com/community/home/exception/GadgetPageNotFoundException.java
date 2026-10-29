package com.community.home.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 없는 가젯 페이지(404 {@code GADGET_PAGE_NOT_FOUND}) — 관리자 API 와 공개 {@code GET /api/pages/{key}} 가 같이 쓴다 */
public class GadgetPageNotFoundException extends CommonException {

    public GadgetPageNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GADGET_PAGE_NOT_FOUND", new MessageRef("error.GADGET_PAGE_NOT_FOUND"), Map.of(), null);
    }
}

package com.community.home.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 시스템 가젯 페이지(V209 — {@code /tips} 의 배치)는 지울 수 없다(409 {@code GADGET_PAGE_SYSTEM}, tips-contract 「/tips 레이아웃 편집」) */
public class GadgetPageSystemException extends CommonException {

    public GadgetPageSystemException() {
        super(HttpStatus.CONFLICT, "GADGET_PAGE_SYSTEM", new MessageRef("error.GADGET_PAGE_SYSTEM"), Map.of(), null);
    }
}

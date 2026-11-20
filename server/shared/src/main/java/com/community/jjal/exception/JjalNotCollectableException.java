package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 403 — 19금 게시판·19금 글의 짤이라 담을 수 없다(my-jjal-contract 정책 5) */
public class JjalNotCollectableException extends CommonException {

    public JjalNotCollectableException() {
        super(HttpStatus.FORBIDDEN, "JJAL_NOT_COLLECTABLE", new MessageRef("error.JJAL_NOT_COLLECTABLE"));
    }
}

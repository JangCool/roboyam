package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 「끝난 딜에서 새 딜 올리기」는 끝난(종료·기간 종료) 딜에서만 — 진행 중인 딜이다(409) */
public class DealNotEndedException extends CommonException {

    public DealNotEndedException() {
        super(HttpStatus.CONFLICT, "DEAL_NOT_ENDED", new MessageRef("error.DEAL_NOT_ENDED"));
    }
}
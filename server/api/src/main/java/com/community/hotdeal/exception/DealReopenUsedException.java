package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 작성자 되돌리기(D2)는 글마다 한 번(409) */
public class DealReopenUsedException extends CommonException {

    public DealReopenUsedException() {
        super(HttpStatus.CONFLICT, "DEAL_REOPEN_USED", new MessageRef("error.DEAL_REOPEN_USED"));
    }
}

package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 「아직 진행 중」은 의심(FLAGGED)인 딜에서만(409) */
public class DealNotFlaggedException extends CommonException {

    public DealNotFlaggedException() {
        super(HttpStatus.CONFLICT, "DEAL_NOT_FLAGGED", new MessageRef("error.DEAL_NOT_FLAGGED"));
    }
}

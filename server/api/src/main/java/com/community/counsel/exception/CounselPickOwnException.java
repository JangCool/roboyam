package com.community.counsel.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내 답은 고를 수 없다(409) */
public class CounselPickOwnException extends CommonException {

    public CounselPickOwnException() {
        super(HttpStatus.CONFLICT, "COUNSEL_PICK_OWN", new MessageRef("error.COUNSEL_PICK_OWN"));
    }
}

package com.community.counsel.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 사연을 쓴 사람만 조언을 고른다(403) */
public class CounselPickForbiddenException extends CommonException {

    public CounselPickForbiddenException() {
        super(HttpStatus.FORBIDDEN, "COUNSEL_PICK_FORBIDDEN", new MessageRef("error.COUNSEL_PICK_FORBIDDEN"));
    }
}

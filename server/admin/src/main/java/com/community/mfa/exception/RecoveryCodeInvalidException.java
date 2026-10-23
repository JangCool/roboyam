package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 복구 코드가 맞지 않거나 이미 썼다(400 {@code RECOVERY_CODE_INVALID}). 실패 횟수에 센다 */
public class RecoveryCodeInvalidException extends CommonException {

    public RecoveryCodeInvalidException() {
        super(HttpStatus.BAD_REQUEST, "RECOVERY_CODE_INVALID", new MessageRef("error.RECOVERY_CODE_INVALID"));
    }
}

package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 410 — 발급 30분이 지났거나 이미 끝난(실패한) 인증 번호 — 새로 시작해야 한다 */
public class IdentityVerificationExpiredException extends CommonException {

    public IdentityVerificationExpiredException() {
        super(HttpStatus.GONE, "IDENTITY_VERIFICATION_EXPIRED", new MessageRef("error.IDENTITY_VERIFICATION_EXPIRED"));
    }
}

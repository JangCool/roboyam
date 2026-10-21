package com.community.adminauth.exception;

import com.community.zconfiguration.message.MessageRef;

/** 승인번호가 틀렸다(400 {@code CERT_CODE_INVALID}). 문구에 남은 시도 횟수가 실린다 */
public class CertCodeInvalidException extends CertCodeException {

    public CertCodeInvalidException(int attemptsLeft) {
        super("CERT_CODE_INVALID", new MessageRef("error.CERT_CODE_INVALID", attemptsLeft));
    }
}

package com.community.adminauth.exception;

import com.community.zconfiguration.message.MessageRef;

/** 승인번호의 유효 시간이 지났다(400 {@code CERT_CODE_EXPIRED}) */
public class CertCodeExpiredException extends CertCodeException {

    public CertCodeExpiredException() {
        super("CERT_CODE_EXPIRED", new MessageRef("error.CERT_CODE_EXPIRED"));
    }
}

package com.community.adminauth.exception;

import com.community.zconfiguration.message.MessageRef;

/** 받은 승인번호가 없다(400 {@code CERT_CODE_NOT_FOUND}) — 없는 이메일도 같은 응답이다(계정 존재를 알리지 않는다) */
public class CertCodeNotFoundException extends CertCodeException {

    public CertCodeNotFoundException() {
        super("CERT_CODE_NOT_FOUND", new MessageRef("error.CERT_CODE_NOT_FOUND"));
    }
}

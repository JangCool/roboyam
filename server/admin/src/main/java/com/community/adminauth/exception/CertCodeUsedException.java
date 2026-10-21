package com.community.adminauth.exception;

import com.community.zconfiguration.message.MessageRef;

/** 이미 쓴 승인번호다(400 {@code CERT_CODE_USED}) */
public class CertCodeUsedException extends CertCodeException {

    public CertCodeUsedException() {
        super("CERT_CODE_USED", new MessageRef("error.CERT_CODE_USED"));
    }
}

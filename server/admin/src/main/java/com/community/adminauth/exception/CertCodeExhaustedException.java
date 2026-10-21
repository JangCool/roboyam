package com.community.adminauth.exception;

import com.community.zconfiguration.message.MessageRef;

/** 승인번호를 너무 많이 틀려 소진됐다(400 {@code CERT_CODE_EXHAUSTED}) — 다시 받아야 한다 */
public class CertCodeExhaustedException extends CertCodeException {

    public CertCodeExhaustedException() {
        super("CERT_CODE_EXHAUSTED", new MessageRef("error.CERT_CODE_EXHAUSTED"));
    }
}

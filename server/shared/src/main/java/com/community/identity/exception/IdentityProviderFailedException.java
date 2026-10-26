package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 502 — 본인인증 업체 오류·인증 미완료·결과를 읽지 못함(원인은 로그에만, 업체 응답 본문은 싣지 않는다) */
public class IdentityProviderFailedException extends CommonException {

    public IdentityProviderFailedException() {
        super(HttpStatus.BAD_GATEWAY, "IDENTITY_PROVIDER_FAILED", new MessageRef("error.IDENTITY_PROVIDER_FAILED"));
    }
}

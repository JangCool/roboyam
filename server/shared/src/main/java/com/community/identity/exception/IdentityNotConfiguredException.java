package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 503 — 포트원 구현인데 포트원 설정(상점 ID·API 비밀키·본인인증 채널 키) 중 빈 칸이 있다 */
public class IdentityNotConfiguredException extends CommonException {

    public IdentityNotConfiguredException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_NOT_CONFIGURED", new MessageRef("error.IDENTITY_NOT_CONFIGURED"));
    }
}

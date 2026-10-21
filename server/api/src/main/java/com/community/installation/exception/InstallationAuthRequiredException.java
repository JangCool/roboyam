package com.community.installation.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 설치 토큰을 아직 확인하지 않은 세션이 토큰 인증 뒤에만 부를 수 있는 마법사 API 를 불렀다(401 {@code INSTALL_AUTH_REQUIRED}). 세션이 30분 동안 쉬어 만료된 경우도 같다 */
public class InstallationAuthRequiredException extends CommonException {

    public InstallationAuthRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "INSTALL_AUTH_REQUIRED", new MessageRef("error.INSTALL_AUTH_REQUIRED"));
    }
}

package com.community.installation.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 설치 토큰이 맞지 않는다(401 {@code INSTALL_TOKEN_INVALID}). 입력한 토큰은 어디에도 남기지 않는다 */
public class InstallationTokenInvalidException extends CommonException {

    public InstallationTokenInvalidException() {
        super(HttpStatus.UNAUTHORIZED, "INSTALL_TOKEN_INVALID", new MessageRef("error.INSTALL_TOKEN_INVALID"));
    }
}

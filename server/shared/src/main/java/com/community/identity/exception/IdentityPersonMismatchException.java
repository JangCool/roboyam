package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이름 채우기 재인증(이미 인증했지만 이름 암호문이 없는 회원)에서 다른 사람(DI·CI 가 저장된 값과 다름)이 인증했다. 시도는 FAILED 로 끝난다 */
public class IdentityPersonMismatchException extends CommonException {

    public IdentityPersonMismatchException() {
        super(HttpStatus.CONFLICT, "IDENTITY_PERSON_MISMATCH", new MessageRef("error.IDENTITY_PERSON_MISMATCH"));
    }
}

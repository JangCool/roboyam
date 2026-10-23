package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 2차 대기 세션이 없거나 5분이 지났거나 그 단계가 아니다(401 {@code MFA_PENDING_EXPIRED}) — 화면은 처음부터 다시 로그인시킨다 */
public class MfaPendingExpiredException extends CommonException {

    public MfaPendingExpiredException() {
        super(HttpStatus.UNAUTHORIZED, "MFA_PENDING_EXPIRED", new MessageRef("error.MFA_PENDING_EXPIRED"));
    }
}

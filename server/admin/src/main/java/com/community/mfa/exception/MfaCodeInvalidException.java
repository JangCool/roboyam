package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** OTP 가 맞지 않는다(400 {@code MFA_CODE_INVALID}) — 응답에 잠기기 전 남은 시도 횟수 {@code remainingAttempts} 가 실린다 */
public class MfaCodeInvalidException extends CommonException {

    public MfaCodeInvalidException(int remainingAttempts) {
        super(HttpStatus.BAD_REQUEST, "MFA_CODE_INVALID", new MessageRef("error.MFA_CODE_INVALID", remainingAttempts), Map.of(), null, remainingAttempts);
    }
}

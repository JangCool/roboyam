package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 2차 인증 코드를 한도만큼 틀려 잠겼다(429 {@code MFA_LOCKED}) — {@code retryAfterSeconds}·{@code Retry-After} 가 실린다. 한도에 닿은 그 실패의 응답부터 이것이다 */
public class MfaLockedException extends CommonException {

    public MfaLockedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "MFA_LOCKED", new MessageRef("error.MFA_LOCKED"), Map.of(), retryAfterSeconds);
    }
}

package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * 409 — 탈퇴한 지 30일(설정값)이 안 된 같은 사람(DI 또는 CI)이 다시 인증하려 했다(commerce.md 12-1 · 17-2). 오류 본문 최상위 {@code retryAfter} = 다시 할 수 있는 서비스 날짜
 * ({@code YYYY-MM-DD}, 한국 시간 — 그날은 언제 해도 된다).
 */
public class IdentityReverifyBlockedException extends CommonException {

    public IdentityReverifyBlockedException(LocalDate retryAfter) {
        super(HttpStatus.CONFLICT, "IDENTITY_REVERIFY_BLOCKED", new MessageRef("error.IDENTITY_REVERIFY_BLOCKED"));
        extra("retryAfter", retryAfter.toString());
    }
}

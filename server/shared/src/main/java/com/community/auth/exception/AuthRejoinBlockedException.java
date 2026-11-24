package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 탈퇴 뒤 재가입 제한(commerce.md 17-2, 30일 — 제재 중 탈퇴면 더 길게) 중인 이메일·소셜 계정으로 가입하려 했다(409 {@code REJOIN_BLOCKED}).
 * 문구에 다시 가입할 수 있는 날(서비스 날짜)을 싣고 {@code retryAfterSeconds} 에 남은 초, 최상위 칸 {@code rejoinAvailableAt} 에 그 시각을 싣는다 — 회원이 헤매지 않게(분석 문서 5-5).
 * 탈퇴한 회원 본인이 보는 문구다(가입 폼의 이메일 중복 확인 API 는 이 제한을 알리지 않는다).
 */
public class AuthRejoinBlockedException extends CommonException {

    public AuthRejoinBlockedException(Instant availableAt, Instant now) {
        super(HttpStatus.CONFLICT, "REJOIN_BLOCKED", new MessageRef("error.REJOIN_BLOCKED", ServiceTime.dateOf(availableAt).toString()), Map.of(),
                Math.max(1, Duration.between(now, availableAt).toSeconds()));
        extra("rejoinAvailableAt", availableAt.toString());
    }
}

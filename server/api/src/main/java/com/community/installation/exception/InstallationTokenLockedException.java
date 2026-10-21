package com.community.installation.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 설치 토큰을 여러 번 틀려 잠겼다(429 {@code INSTALL_TOKEN_LOCKED}). IP 당 연속 5회 실패하면 10분 잠기고, 서버 전체로도 분당 시도 상한이 있다.
 * {@code retryAfterSeconds} 뒤에 다시 시도할 수 있다(응답 본문과 {@code Retry-After} 헤더).
 */
public class InstallationTokenLockedException extends CommonException {

    public InstallationTokenLockedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "INSTALL_TOKEN_LOCKED", new MessageRef("error.INSTALL_TOKEN_LOCKED"),
                Map.of(), retryAfterSeconds);
    }
}

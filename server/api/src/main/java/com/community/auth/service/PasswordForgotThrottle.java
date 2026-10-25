package com.community.auth.service;

import com.community.auth.config.AuthProperties;
import com.community.zconfiguration.crypto.Digests;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 찾기의 「같은 이메일로 60초 안 반복은 조용히 무시」 — <b>이메일 단위, 계정이 있든 없든 똑같이</b> 센다(이 서버 메모리, DB 를 읽지 않는다).
 * 요청 스레드가 계정을 조회하지 않게 하려는 자리다(응답 시간으로 계정 존재를 알리지 않기 위해). 키는 이메일의 해시라 원문을 들지 않는다.
 *
 * <p>서버가 여러 대면 대수만큼 더 보낼 수 있다 — 비밀번호가 있는 회원은 작업 쪽이 토큰 발급 시각(DB)으로 한 번 더 막는다({@link PasswordResetTokenService}).
 * 기록은 창이 지난 것부터 지우고, 그래도 {@link #MAX_ENTRIES} 를 넘으면(무작위 주소를 쏟아붓는 경우) 새 요청은 조용히 무시한다 — 응답은 똑같이 202 다.
 */
@Component
public class PasswordForgotThrottle {

    static final int MAX_ENTRIES = 50_000;

    private final ConcurrentHashMap<String, Long> lastRequested = new ConcurrentHashMap<>();
    private final AuthProperties props;

    public PasswordForgotThrottle(AuthProperties props) {
        this.props = props;
    }

    /**
     * 이번 요청을 처리해도 되는가. 처리하면 지금을 기록한다.
     *
     * @param email 정규화한 이메일
     */
    public boolean tryAcquire(String email) {
        long now = System.currentTimeMillis();
        long window = props.resendCooldownSeconds() * 1000L;
        if (lastRequested.size() >= MAX_ENTRIES) {
            lastRequested.values().removeIf(at -> now - at >= window);
            if (lastRequested.size() >= MAX_ENTRIES) {
                return false;
            }
        }
        String key = Digests.sha256Hex("member-forgot:" + email);
        boolean[] acquired = {false};
        lastRequested.compute(key, (k, at) -> {
            if (at != null && now - at < window) {
                return at;
            }
            acquired[0] = true;
            return now;
        });
        return acquired[0];
    }
}

package com.community.adminauth.service;

import com.community.adminauth.config.AdminAuthProperties;
import com.community.adminauth.exception.AdminLoginLockedException;
import com.community.auth.entity.LoginAttemptEntity;
import com.community.auth.repository.master.LoginAttemptRepositoryQDSL;
import com.community.notification.service.AdminNotices;
import com.community.operator.repository.master.OperatorRepository;
import com.community.zconfiguration.crypto.Digests;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인 시도 제한: 같은 이메일로 연속 5회 틀리면 10분 잠근다(설정값 {@code roboyam.admin-auth.*}). 공개 사이트의 {@code LoginAttemptService} 와 같은 방식이다.
 *
 * <ul>
 *   <li><b>이메일 단위</b>로 세고 계정이 없는 이메일도 똑같이 센다(계정 존재를 알리지 않기 위해). 키는 {@code "admin:" + 정규화한 이메일} 의 SHA-256 이다 —
 *       공개 사이트 회원과 같은 이메일이어도 서로의 횟수·잠금에 섞이지 않는다(같은 {@code login_attempts} 표를 쓴다).</li>
 *   <li>5번째 틀린 응답까지는 401 이고, 그 5번째 실패가 잠금을 시작한다 — 그 뒤 요청이 429 다. 잠긴 동안은 맞는 비밀번호도 거절한다. 잠금은 시간이 지나면 저절로 풀린다.</li>
 *   <li>각 메서드는 <b>짧은 독립 트랜잭션</b>이다 — 로그인 요청이 오류로 끝나도 실패 기록은 남고, 비밀번호 해시 비교(느린 연산)는 트랜잭션 밖에서 한다.</li>
 * </ul>
 * 이메일 원문을 로그에 남기지 않는다.
 */
@Service
public class AdminLoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginAttemptService.class);

    private final LoginAttemptRepositoryQDSL attempts;
    private final OperatorRepository operators;
    private final AdminAuthProperties props;
    private final AdminNotices notices;

    public AdminLoginAttemptService(LoginAttemptRepositoryQDSL attempts, OperatorRepository operators, AdminAuthProperties props, AdminNotices notices) {
        this.attempts = attempts;
        this.operators = operators;
        this.props = props;
        this.notices = notices;
    }

    /**
     * 잠겨 있으면 {@code 429 LOGIN_LOCKED}.
     *
     * @param email 정규화한 이메일
     */
    @Transactional(readOnly = true)
    public void assertNotLocked(String email) {
        Instant now = Instant.now();
        attempts.findLockedUntil(key(email)).filter(until -> until.isAfter(now)).ifPresent(until -> {
            throw new AdminLoginLockedException(secondsUntil(until, now));
        });
    }

    /**
     * 실패를 기록한다. 이번 실패가 한도에 닿으면 잠금이 시작되지만 <b>이번 응답은 그대로 401</b> 이다(호출하는 쪽이 401 을 던진다).
     * 이미 잠긴 이메일에 동시에 들어온 요청은 횟수를 더하지 않는다.
     */
    @Transactional
    public void recordFailure(String email) {
        String key = key(email);
        Instant now = Instant.now();
        attempts.insertIfAbsent(key, now);
        LoginAttemptEntity attempt = attempts.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("login_attempts 행을 만들지 못했다"));
        attempt.clearIfLockExpired(now); // 잠금이 풀렸으면 처음부터 다시 센다
        if (attempt.isLocked(now)) {
            return;
        }
        if (attempt.fail(now, props.loginMaxFailures(), props.loginLockSeconds())) {
            log.info("관리자 로그인 실패가 한도에 닿아 잠금을 시작했다 key={}…", key.substring(0, 8));
            operators.findByEmail(email).ifPresent(operator -> notices.lockedByFailures(operator.getId(), operator.getOperatorName())); // 잠금이 시작되는 이 순간 한 번만(잠긴 동안의 실패는 위에서 돌아간다)
        }
    }

    /**
     * 비밀번호가 맞았다(로그인·비밀번호 재설정) — 실패 횟수·잠금을 지운다. 마지막 로그인 시각은 세션이 완성될 때(관리자 2차 인증 뒤) {@code MfaLoginService#complete} 가 갱신한다
     */
    @Transactional
    public void resetFailures(String email) {
        attempts.findForUpdate(key(email)).ifPresent(attempt -> attempt.reset(Instant.now()));
    }

    /** 이메일 원문을 저장하지 않는 키 — 공개 사이트 회원 로그인과 섞이지 않게 접두어를 붙인다 */
    static String key(String email) {
        return Digests.sha256Hex("admin:" + email);
    }

    /** 남은 초(올림, 최소 1) */
    private static long secondsUntil(Instant target, Instant now) {
        long millis = Duration.between(now, target).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}

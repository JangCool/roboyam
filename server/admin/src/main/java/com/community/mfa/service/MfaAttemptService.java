package com.community.mfa.service;

import com.community.adminauth.config.AdminAuthProperties;
import com.community.auth.entity.LoginAttemptEntity;
import com.community.auth.repository.master.LoginAttemptRepositoryQDSL;
import com.community.mfa.exception.MfaLockedException;
import com.community.zconfiguration.crypto.Digests;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2차 인증 코드 실패 제한 — {@code login_attempts} 표의 키 {@code sha256("mfa:" + 운영자 ID)}(이메일 로그인 키 {@code "admin:"+이메일} · 공개 사이트 회원 키와 섞이지 않는다).
 *
 * <ul>
 *   <li>한도 = 로그인 보안 설정의 {@code max_failures}(3~10), 잠금 = 관리자 로그인 잠금 시간({@code roboyam.admin-auth.login-lock-seconds}, 10분).</li>
 *   <li>대기 세션의 verify·강제 등록 확정과 로그인 세션의 복구 코드 새로 만들기·자발 등록 확정이 <b>같은 키</b>를 쓴다. 성공하면 지운다.</li>
 *   <li>한도에 닿는 그 실패부터 {@code 429 MFA_LOCKED}(목업·화면과 같은 약속). 그 전까지는 남은 횟수를 돌려준다.</li>
 *   <li>실패 기록은 <b>독립 트랜잭션</b>이다 — 요청이 오류로 끝나 업무 트랜잭션이 롤백돼도 횟수는 남는다.</li>
 * </ul>
 */
@Service
public class MfaAttemptService {

    private static final Logger log = LoggerFactory.getLogger(MfaAttemptService.class);

    private final LoginAttemptRepositoryQDSL attempts;
    private final AdminAuthProperties props;
    private final Clock clock;

    public MfaAttemptService(LoginAttemptRepositoryQDSL attempts, AdminAuthProperties props, Clock clock) {
        this.attempts = attempts;
        this.props = props;
        this.clock = clock;
    }

    /** 잠겨 있으면 {@code 429 MFA_LOCKED} */
    @Transactional(readOnly = true)
    public void assertNotLocked(long operatorId) {
        Instant now = clock.instant();
        attempts.findLockedUntil(key(operatorId)).filter(until -> until.isAfter(now)).ifPresent(until -> {
            throw new MfaLockedException(secondsUntil(until, now));
        });
    }

    /**
     * 실패를 하나 센다.
     *
     * @return 잠기기 전 남은 횟수(1 이상)
     * @throws MfaLockedException 이번 실패로 한도에 닿았거나 이미 잠겨 있을 때
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = MfaLockedException.class) // 잠금을 알리는 예외로 끝나도 잠금은 남는다
    public int recordFailure(long operatorId, int maxFailures) {
        String key = key(operatorId);
        Instant now = clock.instant();
        attempts.insertIfAbsent(key, now);
        LoginAttemptEntity attempt = attempts.findForUpdate(key).orElseThrow(() -> new IllegalStateException("login_attempts 행을 만들지 못했다"));
        attempt.clearIfLockExpired(now);
        if (attempt.isLocked(now)) {
            throw new MfaLockedException(secondsUntil(attempt.getLockedUntil(), now));
        }
        if (attempt.fail(now, maxFailures, props.loginLockSeconds())) {
            log.info("2차 인증 실패가 한도에 닿아 잠갔다 operatorId={}", operatorId);
            throw new MfaLockedException(props.loginLockSeconds());
        }
        return maxFailures - attempt.getFailCount();
    }

    /** 성공 — 횟수·잠금을 지운다(부르는 쪽의 트랜잭션에 참여한다) */
    @Transactional
    public void reset(long operatorId) {
        attempts.findForUpdate(key(operatorId)).ifPresent(attempt -> attempt.reset(clock.instant()));
    }

    /** 운영자 ID 의 키 — 이메일 로그인 키와 접두어로 가른다 */
    public static String key(long operatorId) {
        return Digests.sha256Hex("mfa:" + operatorId);
    }

    private static long secondsUntil(Instant target, Instant now) {
        long millis = Duration.between(now, target).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}

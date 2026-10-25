package com.community.account.service;

import com.community.account.exception.AccountCurrentPasswordWrongException;
import com.community.auth.service.LoginAttemptService;
import com.community.auth.service.PasswordMatching;
import com.community.user.entity.UserEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 로그인한 회원의 「현재 비밀번호」 확인 — 비밀번호 변경·이메일 변경이 쓴다. <b>로그인 실패와 같은 횟수</b>({@code login_attempts}, 그 회원 이메일 키)로 센다:
 * 잠겨 있으면 {@code 429 LOGIN_LOCKED}(+{@code retryAfterSeconds}), 틀리면 실패 1회 + {@code 401 CURRENT_PASSWORD_WRONG}(5번째 틀림이 10분 잠금을 시작한다).
 * 해시 비교는 트랜잭션 밖이고, 실패 기록은 {@link LoginAttemptService} 의 짧은 트랜잭션이 남긴다(요청이 오류로 끝나도 남는다).
 */
@Component
public class CurrentPasswordVerifier {

    private final LoginAttemptService attempts;
    private final PasswordEncoder encoder;

    public CurrentPasswordVerifier(LoginAttemptService attempts, PasswordEncoder encoder) {
        this.attempts = attempts;
        this.encoder = encoder;
    }

    /**
     * @throws com.community.auth.exception.AuthLoginLockedException 잠겨 있을 때(429)
     * @throws AccountCurrentPasswordWrongException                 맞지 않을 때(401)
     */
    public void verify(UserEntity user, String rawPassword) {
        String key = attemptKey(user);
        attempts.assertNotLocked(key);
        if (!PasswordMatching.matches(encoder, rawPassword, user.getPasswordHash())) {
            attempts.recordFailure(key);
            throw new AccountCurrentPasswordWrongException();
        }
    }

    /** 로그인 실패 횟수의 키 — 로그인과 같은 정규화한 이메일. 이메일이 없는 비밀번호 계정(있을 수 없지만)은 회원 ID 로 따로 센다 */
    static String attemptKey(UserEntity user) {
        return user.getEmail() != null ? user.getEmail() : "#user:" + user.getId();
    }
}

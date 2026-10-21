package com.community.auth.service;

import com.community.auth.dto.AuthUser;
import com.community.auth.exception.AuthInvalidCredentialsException;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 이메일 로그인 확인.
 *
 * <ul>
 *   <li>실패 원인(없는 이메일·틀린 비밀번호·소셜로만 가입한 계정·탈퇴한 계정)은 밖으로 구분해 알리지 않고 모두 같은 {@code 401 INVALID_CREDENTIALS} 다.</li>
 *   <li>없는 이메일·비밀번호 없는 계정에도 <b>비밀번호 해시 비교를 한 번</b> 해서 응답 시간으로 계정 존재를 알 수 없게 한다.</li>
 *   <li>5회 연속 실패하면 10분 잠근다({@link LoginAttemptService}). 잠금 확인은 해시 비교보다 앞이라 잠긴 동안은 맞는 비밀번호도 거절한다.</li>
 *   <li>72바이트를 넘는 비밀번호는 bcrypt 가 다룰 수 없어 — 예외(500) 대신 틀린 비밀번호로 처리한다(비밀번호 규칙이 72바이트 이하라 정상 비밀번호가 아니다).</li>
 *   <li>이 클래스는 트랜잭션을 열지 않는다 — 느린 해시 비교 동안 DB 연결을 붙잡지 않고, 실패 기록은 {@link LoginAttemptService} 의 짧은 트랜잭션이 남긴다.</li>
 * </ul>
 * 세션 만들기는 웹 계층의 일이다(컨트롤러의 {@code LoginSession}). 이메일·비밀번호를 로그에 남기지 않는다.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    /** 계정이 없을 때도 해시 비교에 드는 시간을 비슷하게 맞추기 위한 값 */
    private static final String TIMING_DUMMY_PASSWORD = "roboyam-timing-dummy-password-1";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final LoginAttemptService attempts;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder encoder, LoginAttemptService attempts) {
        this.users = users;
        this.encoder = encoder;
        this.attempts = attempts;
        this.dummyHash = encoder.encode(TIMING_DUMMY_PASSWORD);
    }

    /**
     * 이메일·비밀번호를 확인한다. 성공하면 횟수를 지우고 마지막 로그인 시각을 갱신한 뒤 로그인 주체를 돌려준다.
     *
     * @throws ValidationException           이메일 형식이 아니거나 비밀번호가 비었을 때(400 — 시도 횟수에 세지 않는다)
     * @throws com.community.auth.exception.AuthLoginLockedException 잠겨 있을 때(429)
     * @throws AuthInvalidCredentialsException 맞지 않을 때(401)
     */
    public AuthUser authenticate(String rawEmail, String password) {
        String email = UserPolicy.normalizeEmail(rawEmail);
        UserPolicy.emailViolation(email).ifPresent(message -> {
            throw ValidationException.of("email", message);
        });
        if (password == null || password.isEmpty()) {
            throw ValidationException.of("password", new MessageRef("user.password.required"));
        }

        attempts.assertNotLocked(email);

        Optional<UserEntity> found = users.findByEmail(email);
        boolean hasPassword = found.isPresent() && found.get().hasPassword();
        boolean matches = passwordMatches(password, hasPassword ? found.get().getPasswordHash() : dummyHash) && hasPassword;

        // 탈퇴하거나 지워진 계정은 로그인할 수 없다. 정지·영구 정지 계정은 로그인은 된다(제한은 응답의 restriction 과 각 기능에서)
        if (!matches || found.get().isGone()) {
            attempts.recordFailure(email);
            log.info("로그인 실패 사유={}", found.isEmpty() ? "없는 계정" : !hasPassword ? "비밀번호 없는 계정" : !matches ? "비밀번호 불일치" : "탈퇴한 계정");
            throw new AuthInvalidCredentialsException();
        }

        UserEntity user = found.get();
        attempts.recordSuccess(email, user.getId());
        return new AuthUser(user.getId(), user.getRole());
    }

    /** 해시 비교. 72바이트가 넘는 비밀번호나 읽을 수 없는 해시는 예외 없이 불일치다(시간을 맞추려고 더미 비교를 한 번 한다) */
    private boolean passwordMatches(String password, String hash) {
        if (password.getBytes(StandardCharsets.UTF_8).length > UserPolicy.PASSWORD_MAX_BYTES) {
            encoder.matches(TIMING_DUMMY_PASSWORD, hash);
            return false;
        }
        try {
            return encoder.matches(password, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

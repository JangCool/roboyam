package com.community.adminauth.service;

import com.community.adminauth.dto.OperatorPrincipal;
import com.community.adminauth.exception.AdminInvalidCredentialsException;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 관리자 앱 로그인 확인 — {@code operators} 기준이다(회원 {@code users} 는 관리자 앱에 들어올 수 없다).
 *
 * <ul>
 *   <li>로그인할 수 있는 조건은 <b>승인됨 · 잠기지 않음 · 만료 표시 없음 · 사용 중 · 계정 기간 안</b>이다({@link OperatorEntity#canLogin}).</li>
 *   <li>실패 원인(없는 이메일·틀린 비밀번호·위 조건을 못 채운 계정)은 밖으로 구분해 알리지 않고 모두 같은 {@code 401 INVALID_CREDENTIALS} 다.
 *       없는 이메일에도 <b>비밀번호 해시 비교를 한 번</b> 해서 응답 시간으로 계정 존재를 알 수 없게 한다.</li>
 *   <li>5회 연속 실패하면 10분 잠근다({@link AdminLoginAttemptService}). 잠금 확인은 해시 비교보다 앞이라 잠긴 동안은 맞는 비밀번호도 거절한다.</li>
 *   <li>72바이트를 넘는 비밀번호는 bcrypt 가 다룰 수 없어 — 예외(500) 대신 틀린 비밀번호로 처리한다.</li>
 *   <li>로그인 성공·실패는 감사 기록으로 남긴다(실패 사유는 기록에만 있고 응답에는 없다). 이메일·비밀번호는 기록에도 로그에도 남기지 않는다.</li>
 *   <li>이 클래스는 트랜잭션을 열지 않는다 — 느린 해시 비교 동안 DB 연결을 붙잡지 않고, 실패 기록은 {@link AdminLoginAttemptService} 의 짧은 트랜잭션이 남긴다.</li>
 * </ul>
 * 세션 만들기는 웹 계층의 일이다(컨트롤러의 {@code AdminSession}).
 */
@Service
public class AdminLoginService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginService.class);

    /** 계정이 없을 때도 해시 비교에 드는 시간을 비슷하게 맞추기 위한 값 */
    private static final String TIMING_DUMMY_PASSWORD = "roboyam-timing-dummy-password-1";

    private final OperatorRepository operators;
    private final PasswordEncoder encoder;
    private final AdminLoginAttemptService attempts;
    private final AuditLogService audit;
    private final String dummyHash;

    public AdminLoginService(OperatorRepository operators, PasswordEncoder encoder, AdminLoginAttemptService attempts, AuditLogService audit) {
        this.operators = operators;
        this.encoder = encoder;
        this.attempts = attempts;
        this.audit = audit;
        this.dummyHash = encoder.encode(TIMING_DUMMY_PASSWORD);
    }

    /**
     * 이메일·비밀번호를 확인한다. 성공하면 횟수를 지우고 마지막 로그인 시각을 갱신한 뒤 로그인 주체를 돌려준다.
     *
     * @param ip 요청자 IP(감사 기록용)
     * @throws ValidationException                                          이메일 형식이 아니거나 비밀번호가 비었을 때(400 — 시도 횟수에 세지 않는다)
     * @throws com.community.adminauth.exception.AdminLoginLockedException    잠겨 있을 때(429)
     * @throws AdminInvalidCredentialsException                              맞지 않을 때(401)
     */
    public OperatorPrincipal authenticate(String rawEmail, String password, String ip) {
        String email = UserPolicy.normalizeEmail(rawEmail);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        UserPolicy.emailViolation(email).ifPresent(message -> invalid.put("email", message));
        if (password == null || password.isEmpty()) {
            invalid.put("password", new MessageRef("user.password.required"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        attempts.assertNotLocked(email);

        Optional<OperatorEntity> found = operators.findByEmail(email);
        boolean matches = passwordMatches(password, found.map(OperatorEntity::getPasswordHash).orElse(dummyHash)) && found.isPresent();
        boolean allowed = found.isPresent() && found.get().canLogin(Instant.now());

        if (!matches || !allowed) {
            attempts.recordFailure(email);
            String reason = found.isEmpty() ? "NO_ACCOUNT" : !matches ? "BAD_PASSWORD" : "NOT_ALLOWED";
            AuditEvent event = AuditEvent.anonymous(ip, AuditActions.LOGIN_FAILURE, AuditResult.FAILURE).with("reason", reason);
            if (found.isPresent()) {
                event = event.actor(found.get().getId()).target("OPERATOR", Long.toString(found.get().getId()));
            } else {
                event = event.with("key", Digests.sha256Hex("admin:" + email).substring(0, 12)); // 이메일 원문 대신 구분용 해시 조각
            }
            audit.record(event);
            log.info("관리자 로그인 실패 사유={}", reason);
            throw new AdminInvalidCredentialsException();
        }

        OperatorEntity operator = found.get();
        attempts.recordSuccess(email, operator.getId());
        audit.record(AuditEvent.byOperator(operator.getId(), ip, AuditActions.LOGIN_SUCCESS, AuditResult.SUCCESS).target("OPERATOR", Long.toString(operator.getId())));
        return new OperatorPrincipal(operator.getId());
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

package com.community.adminauth.service;

import com.community.adminauth.config.AdminPasswordResetProperties;
import com.community.adminauth.dto.PasswordResetCodeIssued;
import com.community.adminauth.dto.response.PasswordResetCodeResponse;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.entity.OperatorCertCodeEntity;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorCertCodeRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.Messages;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 <b>승인번호 요청</b> — 로그인 전 공개 API 다. 승인번호(숫자 6자리, 유효 10분)를 만들어 <b>해시로 저장</b>하고 메일로 보낸다. 새로 받으면 이전 번호는 못 쓴다.
 *
 * <p>계정 열거를 막는 설계(이 클래스가 지키는 것):
 * <ul>
 *   <li><b>응답이 늘 같다</b> — 없는 이메일·잠긴/미승인/만료/기간 밖 계정이어도 같은 200 과 같은 값이다.</li>
 *   <li><b>일이 같다</b> — 요청 한도 확인·기록, 번호 만들기, 승인번호 행 쓰기를 대상 여부와 상관없이 똑같이 한다. 대상이 아니면 <b>가짜 행</b>(운영자 없음, 전달되지 않는 번호)을 만들고
 *       메일·감사 기록만 건너뛴다 — 그래서 뒤의 확인({@link PasswordResetService})도 같은 모양으로 답한다. 메일은 커밋 뒤 <b>다른 스레드</b>가 보내 응답 시간에 영향이 없다.</li>
 *   <li><b>한도도 같다</b> — 이메일 쿨다운 60초·시간당 5회, IP 시간당 20회를 계정이 없어도 똑같이 센다({@link PasswordResetRequestLimiter}).</li>
 * </ul>
 * 승인번호·이메일을 로그에 남기지 않는다. 메일 서버가 없어 못 보내도 요청은 성공으로 끝난다(경고 로그만).
 */
@Service
public class PasswordResetCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OperatorRepository operators;
    private final OperatorCertCodeRepository certs;
    private final PasswordResetRequestLimiter limiter;
    private final AdminPasswordResetProperties props;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    private final Messages messages;

    public PasswordResetCodeService(OperatorRepository operators, OperatorCertCodeRepository certs, PasswordResetRequestLimiter limiter,
                                    AdminPasswordResetProperties props, SnowflakeIdGenerator ids, AuditLogService audit, ApplicationEventPublisher events,
                                    Messages messages) {
        this.operators = operators;
        this.certs = certs;
        this.limiter = limiter;
        this.props = props;
        this.ids = ids;
        this.audit = audit;
        this.events = events;
        this.messages = messages;
    }

    /**
     * 승인번호를 만들어 보낸다(대상일 때만). 검사 순서: 이메일 형식(400, 한도에 세지 않는다) → 요청 한도(429) → 처리.
     *
     * @param ip 요청자 IP(한도 키·감사 기록)
     * @return 늘 같은 값 — 자릿수·유효 분·다시 받기까지의 초
     */
    @Transactional
    public PasswordResetCodeResponse request(String rawEmail, String ip) {
        String email = UserPolicy.normalizeEmail(rawEmail);
        UserPolicy.emailViolation(email).ifPresent(message -> {
            throw ValidationException.of("email", message);
        });

        Instant now = Instant.now();
        String emailKey = certKey(email);
        limiter.consume(emailKey, Digests.sha256Hex("admin-cert-ip:" + ip), now);

        Optional<OperatorEntity> found = operators.findByEmail(email);
        Long operatorId = found.filter(operator -> operator.canLogin(now)).map(OperatorEntity::getId).orElse(null);

        String code = newCode();
        Instant expiresAt = now.plus(Duration.ofMinutes(props.codeValidMinutes()));
        Optional<OperatorCertCodeEntity> existing = certs.findWithLockByEmailKey(emailKey);
        if (existing.isPresent()) {
            existing.get().reissue(operatorId, code, now, expiresAt);
        } else {
            certs.persist(OperatorCertCodeEntity.issue(ids.nextId(), emailKey, operatorId, code, now, expiresAt));
        }

        // 감사 기록도 대상이 아니어도 한 줄 남겨 일의 양을 맞춘다(로그인 실패 기록처럼 이메일 원문 대신 구분용 해시 조각만 싣는다). 메일만 대상에게 간다
        if (operatorId != null) {
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PASSWORD_RESET_REQUEST, AuditResult.SUCCESS).target("OPERATOR", Long.toString(operatorId)));
            events.publishEvent(new PasswordResetCodeIssued(email, code, messages.locale()));
        } else {
            audit.record(AuditEvent.anonymous(ip, AuditActions.PASSWORD_RESET_REQUEST, AuditResult.FAILURE).with("reason", found.isEmpty() ? "NO_ACCOUNT" : "NOT_ALLOWED")
                    .with("key", emailKey.substring(0, 12)));
        }
        return new PasswordResetCodeResponse(AdminPasswordResetProperties.CODE_LENGTH, props.codeValidMinutes(), props.cooldownSeconds());
    }

    /** 이메일 해시 키 — 승인번호 행과 이메일 요청 한도의 키(공개 사이트·로그인 잠금 키와 접두어로 갈린다) */
    static String certKey(String normalizedEmail) {
        return Digests.sha256Hex("admin-cert:" + normalizedEmail);
    }

    /** 숫자 6자리(앞자리 0 허용) — 예측할 수 없는 난수 */
    private static String newCode() {
        return String.format("%0" + AdminPasswordResetProperties.CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
    }
}

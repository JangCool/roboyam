package com.community.mail.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mail.dto.request.MailTestRequest;
import com.community.mail.dto.response.MailTestResultView;
import com.community.mail.entity.MailSettingEntity;
import com.community.mail.repository.master.MailSettingRepository;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import com.community.permission.dto.AdminActor;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 메일 서버 시험 발송 — <b>저장된 설정으로</b> 실제 발송({@link MailDispatcher}, 인증·재설정 메일과 같은 길)을 한 통 한다. 입구 권한은 컨트롤러가 선언한다.
 *
 * <ul>
 *   <li>받는 주소는 형식을 본다(400 {@code fields.to}). 운영자당 분당 {@value MailTestRateLimiter#PER_MINUTE}번(429 {@code MAIL_TEST_RATE_LIMITED}) — 메일 서버에 실제로 접속하는 시험만 센다.</li>
 *   <li>실패는 오류가 아니라 200 {@code succeeded=false} + 원인 코드 + 짧은 문구다. <b>SMTP 응답 원문·자격 증명·서버 주소·받는 주소를 응답·로그·감사에 싣지 않는다</b> —
 *       원인은 예외의 종류만 보고 가르고({@link MailSendFailures}) 로그에는 예외 클래스 이름만 남긴다.</li>
 *   <li>결과는 설정 이력이 아니라 감사 {@code MAIL_SETTINGS_TEST}(성공 여부·원인 코드)에만 남긴다.</li>
 *   <li>트랜잭션을 걸지 않는다 — 메일 서버를 기다리는 동안 DB 연결을 잡지 않는다(감사 기록은 짧은 트랜잭션 하나).</li>
 * </ul>
 */
@Service
public class MailTestService {

    private static final Logger log = LoggerFactory.getLogger(MailTestService.class);

    static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    static final String CRYPTO_KEY_MISSING = "CRYPTO_KEY_MISSING";

    private final MailSettingRepository settings;
    private final MailDispatcher dispatcher;
    private final SecretEncryptor encryptor;
    private final MailTestRateLimiter limiter;
    private final AuditLogService audit;
    private final Messages messages;
    private final NotificationMessageRenderer renderer;

    public MailTestService(MailSettingRepository settings, MailDispatcher dispatcher, SecretEncryptor encryptor, MailTestRateLimiter limiter,
                           AuditLogService audit, Messages messages, NotificationMessageRenderer renderer) {
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.encryptor = encryptor;
        this.limiter = limiter;
        this.audit = audit;
        this.messages = messages;
        this.renderer = renderer;
    }

    public MailTestResultView test(AdminActor actor, MailTestRequest body) {
        String to = UserPolicy.normalizeEmail(body == null ? null : body.to());
        if (to == null || to.isEmpty()) {
            throw new ValidationException(Map.of("to", new MessageRef("mail.test.to.required")));
        }
        if (!UserPolicy.isValidEmail(to)) {
            throw new ValidationException(Map.of("to", new MessageRef("mail.test.to.format")));
        }

        String reason = precheck();
        if (reason == null) {
            limiter.acquire(actor.operatorId());
            reason = send(to);
        }
        boolean succeeded = reason == null;
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MAIL_SETTINGS_TEST, succeeded ? AuditResult.SUCCESS : AuditResult.FAILURE)
                .target("SITE", "mail").with("succeeded", succeeded).with("reason", succeeded ? "OK" : reason));
        log.info("메일 서버 시험 발송 결과={} by operatorId={}", succeeded ? "OK" : reason, actor.operatorId());
        return new MailTestResultView(succeeded, reason, succeeded ? null : messages.get(new MessageRef("mail.test.result." + reason)));
    }

    /** 메일 서버에 접속하기 전에 알 수 있는 실패 — 설정 없음, 비밀번호를 풀 키 없음 */
    private String precheck() {
        Optional<MailSettingEntity> found = settings.findById(MailSettingEntity.ROW_ID);
        if (found.isEmpty()) {
            return NOT_CONFIGURED;
        }
        MailSettingEntity setting = found.get();
        if (setting.getUsername() != null && (setting.getPasswordEnc() == null || !encryptor.isConfigured())) {
            return CRYPTO_KEY_MISSING;
        }
        return null;
    }

    /** @return 실패 원인 코드, 성공이면 null */
    private String send(String to) {
        try {
            // 필수 안내 메일 「메일 서버 시험」 — 운영자가 고른 문구·템플릿(없으면 기본 문구), 요청한 운영자의 언어
            RenderedEmail content = renderer.renderEmail(MessageRenderRequest.requiredMail(RequiredMail.MAIL_TEST)
                    .locale(messages.locale())
                    .build());
            boolean sent = dispatcher.sendTagged(to, content.subject(), content.untaggedSubject(), content.html());
            return sent ? null : NOT_CONFIGURED;
        } catch (RuntimeException e) {
            String reason = MailSendFailures.classify(e);
            // 예외 문구에는 받는 주소·서버 응답·호스트 이름이 실릴 수 있어 클래스 이름만 남긴다
            log.warn("메일 서버 시험 발송 실패 reason={} cause={}", reason, MailSendFailures.causeNames(e));
            return reason;
        }
    }

}

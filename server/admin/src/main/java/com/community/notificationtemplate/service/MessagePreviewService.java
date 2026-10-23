package com.community.notificationtemplate.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mail.dto.response.MailTestResultView;
import com.community.mail.entity.MailSettingEntity;
import com.community.mail.exception.MailTestRateLimitedException;
import com.community.mail.repository.master.MailSettingRepository;
import com.community.mail.service.MailDispatcher;
import com.community.mail.service.MailSendFailures;
import com.community.mail.service.MailTestRateLimiter;
import com.community.notification.channel.ChannelPostResult;
import com.community.notification.channel.NotificationChannelPoster;
import com.community.notification.entity.NotificationChannelEntity;
import com.community.notification.repository.master.NotificationChannelRepository;
import com.community.notification.service.ChannelTestRateLimiter;
import com.community.notificationtemplate.builtin.BuiltinTemplates;
import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageAudience;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageChannelDef;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageTarget;
import com.community.notificationtemplate.catalog.MessageVariable;
import com.community.notificationtemplate.dto.request.ChatMessageTestRequest;
import com.community.notificationtemplate.dto.request.MailMessageTestRequest;
import com.community.notificationtemplate.dto.request.MailPreviewRequest;
import com.community.notificationtemplate.dto.request.MailTemplateSaveRequest;
import com.community.notificationtemplate.dto.request.MessageSaveRequest;
import com.community.notificationtemplate.dto.request.MessageTargetRefInput;
import com.community.notificationtemplate.dto.response.ChatMessageTestResultView;
import com.community.notificationtemplate.dto.response.MailPreviewView;
import com.community.notificationtemplate.engine.ChatTemplateValues;
import com.community.notificationtemplate.engine.MailColor;
import com.community.notificationtemplate.engine.MailHtml;
import com.community.notificationtemplate.engine.MailTemplateValues;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedChat;
import com.community.notificationtemplate.engine.RenderedEmail;
import com.community.notificationtemplate.exception.MessageTargetNotFoundException;
import com.community.notificationtemplate.exception.TemplateNotFoundException;
import com.community.permission.dto.AdminActor;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 알림 문구 미리보기 · 시험 발송 — 렌더링 엔진({@link NotificationMessageRenderer})의 같은 그리기 코드에 <b>저장 전 값</b>과 견본 값을 넣는다.
 *
 * <ul>
 *   <li>미리보기(이메일): 견본 값 + 메일 템플릿(저장된 것 또는 저장 전 값) → {@code {subject, html}}. 모르는 변수는 그대로 보인다.</li>
 *   <li>시험 발송(이메일): 메일 서버 설정 시험과 같은 결과 모양 · 한도(운영자당 분당 5번, 429 {@code MAIL_TEST_RATE_LIMITED}) · 같은 발송 길({@code MailDispatcher}).
 *       받는 주소·SMTP 응답은 응답·로그·감사에 싣지 않는다.</li>
 *   <li>시험 게시(Slack·Mattermost): 고른 그 종류의 알림 도구로 실제로 올린다 — 알림 도구 연결 시험과 같은 길(SSRF 방어 {@code SafeHttpClient}, 사유 코드만)과
 *       같은 한도(연결 시험과 합쳐 운영자당 분당 6번 — 넘으면 이 API 의 계약대로 429 {@code MAIL_TEST_RATE_LIMITED}).</li>
 * </ul>
 * 트랜잭션을 걸지 않는다 — 메일 서버·외부 서비스를 기다리는 동안 DB 연결을 잡지 않는다(감사 기록은 짧은 트랜잭션 하나).
 */
@Service
public class MessagePreviewService {

    private static final Logger log = LoggerFactory.getLogger(MessagePreviewService.class);

    private final TemplateSupport support;
    private final NotificationMessageAdminService messageService;
    private final NotificationMessageRenderer renderer;
    private final MailSettingRepository mailSettings;
    private final SecretEncryptor encryptor;
    private final MailDispatcher dispatcher;
    private final MailTestRateLimiter mailLimiter;
    private final ChannelTestRateLimiter channelLimiter;
    private final NotificationChannelRepository channels;
    private final NotificationChannelPoster poster;
    private final AuditLogService audit;

    public MessagePreviewService(TemplateSupport support, NotificationMessageAdminService messageService, NotificationMessageRenderer renderer,
                                 MailSettingRepository mailSettings, SecretEncryptor encryptor, MailDispatcher dispatcher, MailTestRateLimiter mailLimiter,
                                 ChannelTestRateLimiter channelLimiter, NotificationChannelRepository channels, NotificationChannelPoster poster, AuditLogService audit) {
        this.support = support;
        this.messageService = messageService;
        this.renderer = renderer;
        this.mailSettings = mailSettings;
        this.encryptor = encryptor;
        this.dispatcher = dispatcher;
        this.mailLimiter = mailLimiter;
        this.channelLimiter = channelLimiter;
        this.channels = channels;
        this.poster = poster;
        this.audit = audit;
    }

    // ── 미리보기 ────────────────────────────────────────────────────────────

    public MailPreviewView preview(MailPreviewRequest body) {
        MailPreviewRequest request = body == null ? new MailPreviewRequest(null, null, null, null, null, null) : body;
        String locale = MessageLocales.valid(request.locale()) ? request.locale() : null;
        if (locale == null) {
            throw new ValidationException(Map.of("locale", new MessageRef("notificationTemplate.problem.locale")));
        }
        MessageTarget target = optionalTarget(request.target());
        MailTemplateValues template;
        if (request.template() != null) {
            template = unsaved(request.template(), target);
        } else if (request.templateId() != null && !request.templateId().isBlank()) {
            template = support.mailTemplate(request.templateId().strip()).orElseThrow(TemplateNotFoundException::new);
        } else {
            template = support.mailTemplate(BuiltinTemplates.mailId(target == null ? MessageAudience.MEMBER : target.audience())).orElseThrow();
        }
        Map<String, String> samples = target != null ? support.samples(target, emailDef(target)) : support.commonSamples(template.audience());
        RenderedEmail rendered = renderer.previewEmail(locale, request.subject() == null ? "" : request.subject(), MailHtml.sanitize(request.bodyHtml()), template, samples,
                true);
        return new MailPreviewView(rendered.subject(), rendered.html());
    }

    // ── 이메일 시험 발송 ────────────────────────────────────────────────────

    public MailTestResultView test(AdminActor actor, MailMessageTestRequest body) {
        MailMessageTestRequest request = body == null ? new MailMessageTestRequest(null, null, null, null, null, null) : body;
        String to = UserPolicy.normalizeEmail(request.to());
        if (to == null || to.isEmpty()) {
            throw new ValidationException(Map.of("to", new MessageRef("mail.test.to.required")));
        }
        if (!UserPolicy.isValidEmail(to)) {
            throw new ValidationException(Map.of("to", new MessageRef("mail.test.to.format")));
        }
        MessageTarget target = requiredTarget(request.target());
        MessageChannelDef def = emailDef(target);
        String templateId = request.templateId() == null || request.templateId().isBlank() ? BuiltinTemplates.mailId(target.audience()) : request.templateId().strip();
        NotificationMessageAdminService.Clean clean = messageService.validate(target, MessageChannel.EMAIL, new MessageSaveRequest(target.type().name(), target.code(),
                MessageChannel.EMAIL.name(), "test", request.locale(), templateId, request.subject(), request.bodyHtml()));
        MailTemplateValues template = support.mailTemplate(clean.templateId()).orElseThrow(TemplateNotFoundException::new);

        String reason = precheck();
        if (reason == null) {
            mailLimiter.acquire(actor.operatorId());
            RenderedEmail rendered = renderer.previewEmail(clean.locale(), clean.subject(), clean.body(), template, support.samples(target, def), false);
            reason = send(to, rendered);
        }
        boolean succeeded = reason == null;
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_MESSAGE_TEST, succeeded ? AuditResult.SUCCESS : AuditResult.FAILURE)
                .target("NOTIFICATION_MESSAGE_TARGET", target.type().name() + ":" + target.code()).with("channel", MessageChannel.EMAIL.name())
                .with("succeeded", succeeded).with("reason", succeeded ? "OK" : reason));
        log.info("알림 문구 이메일 시험 발송 결과={} target={} by operatorId={}", succeeded ? "OK" : reason, target.code(), actor.operatorId());
        return new MailTestResultView(succeeded, reason, succeeded ? null : support.messages().get(new MessageRef("mail.test.result." + reason)));
    }

    /** 메일 서버에 접속하기 전에 알 수 있는 실패 — 설정 없음, 비밀번호를 풀 키 없음(메일 서버 설정 시험과 같은 코드) */
    private String precheck() {
        Optional<MailSettingEntity> found = mailSettings.findById(MailSettingEntity.ROW_ID);
        if (found.isEmpty()) {
            return "NOT_CONFIGURED";
        }
        MailSettingEntity setting = found.get();
        if (setting.getUsername() != null && (setting.getPasswordEnc() == null || !encryptor.isConfigured())) {
            return "CRYPTO_KEY_MISSING";
        }
        return null;
    }

    private String send(String to, RenderedEmail rendered) {
        try {
            return dispatcher.sendTagged(to, rendered.subject(), rendered.untaggedSubject(), rendered.html()) ? null : "NOT_CONFIGURED";
        } catch (RuntimeException e) {
            String reason = MailSendFailures.classify(e);
            // 예외 문구에는 받는 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.warn("알림 문구 이메일 시험 발송 실패 reason={} cause={}", reason, MailSendFailures.causeNames(e));
            return reason;
        }
    }

    // ── Slack · Mattermost 시험 게시 ────────────────────────────────────────

    public ChatMessageTestResultView chatTest(AdminActor actor, ChatMessageTestRequest body) {
        ChatMessageTestRequest request = body == null ? new ChatMessageTestRequest(null, null, null, null, null, null, null) : body;
        MessageChannel tool = MessageChannel.of(request.channel()).filter(MessageChannel::chat)
                .orElseThrow(() -> new ValidationException(Map.of("channel", new MessageRef("notificationTemplate.problem.chatChannel"))));
        MessageTarget target = requiredTarget(request.target());
        MessageChannelDef def = target.channel(tool).orElseThrow(() -> new ValidationException(Map.of("channel",
                new MessageRef("notificationTemplate.problem.channel.notInTarget", support.targetName(target), support.channelName(tool)))));

        Long channelId = TemplateSupport.parseId(request.channelId());
        if (channelId == null) {
            throw new ValidationException(Map.of("channelId", new MessageRef("notificationTemplate.problem.channelId")));
        }
        NotificationChannelEntity channel = channels.findById(channelId).orElse(null);
        if (channel == null || !channel.getChannelType().name().equals(tool.name())) {
            throw new ValidationException(Map.of("channelId", new MessageRef("notificationTemplate.problem.channelId.type")));
        }
        if (!channel.isEnabled()) {
            throw new ValidationException(Map.of("channelId", new MessageRef("notificationTemplate.problem.channelId.disabled")));
        }
        NotificationMessageAdminService.Clean clean = messageService.validate(target, tool, new MessageSaveRequest(target.type().name(), target.code(), tool.name(), "test",
                request.locale(), request.templateId(), request.subject(), request.body()));
        ChatTemplateValues template = support.chatTemplate(clean.templateId(), tool).orElseThrow(TemplateNotFoundException::new);

        long retry = channelLimiter.tryAcquire(actor.operatorId()); // 실제로 외부에 요청을 보내는 시험만 센다(연결 시험과 같은 창)
        if (retry > 0) {
            throw new MailTestRateLimitedException(retry);
        }
        Set<String> links = def.variables().stream().filter(MessageVariable::link).map(MessageVariable::name).collect(Collectors.toSet());
        RenderedChat rendered = renderer.previewChat(tool, template, clean.subject(), clean.body(), support.samples(target, def), links, clean.locale(), target.severity(),
                false);
        ChannelPostResult result = poster.post(channel, rendered);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_MESSAGE_TEST, result.ok() ? AuditResult.SUCCESS : AuditResult.FAILURE)
                .target("NOTIFICATION_MESSAGE_TARGET", target.type().name() + ":" + target.code()).with("channel", tool.name())
                .with("channelId", String.valueOf(channel.getId())).with("ok", result.ok()).with("code", result.code()));
        log.info("알림 문구 시험 게시 결과={} target={} channelId={} by operatorId={}", result.code(), target.code(), channel.getId(), actor.operatorId());
        String message = result.ok() ? support.messages().get(new MessageRef("notificationTemplate.chatTest.OK"))
                : support.messages().get(new MessageRef("notification.channel.test." + result.code()));
        return new ChatMessageTestResultView(result.ok(), message);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private MessageTarget optionalTarget(MessageTargetRefInput ref) {
        if (ref == null || ref.type() == null || ref.code() == null) {
            return null;
        }
        return support.target(ref.type(), ref.code());
    }

    private MessageTarget requiredTarget(MessageTargetRefInput ref) {
        if (ref == null) {
            throw new MessageTargetNotFoundException();
        }
        return support.target(ref.type(), ref.code());
    }

    private static MessageChannelDef emailDef(MessageTarget target) {
        return target.channel(MessageChannel.EMAIL).orElseThrow(() -> new ValidationException(Map.of("target", new MessageRef("notificationTemplate.problem.target"))));
    }

    /** 저장 전 메일 템플릿 값(메일 템플릿 편집 화면의 미리보기) — 검사는 하지 않고 틀만 정화한다 */
    private static MailTemplateValues unsaved(MailTemplateSaveRequest t, MessageTarget target) {
        MailTemplateAudience audience = MailTemplateAudience.of(t.audience() == null ? "" : t.audience())
                .orElse(target == null ? MailTemplateAudience.ANY : MailTemplateAudience.of(target.audience()));
        Map<String, String> footer = t.footer() == null ? Map.of() : t.footer();
        Map<String, String> frames = t.frames() == null ? Map.of() : t.frames();
        return new MailTemplateValues(null, t.name(), false, audience, Boolean.TRUE.equals(t.showLogo()), Boolean.TRUE.equals(t.showSiteName()),
                MailColor.of(t.headerColor()).orElse(null),
                Map.of(MessageLocales.KO, nz(footer.get(MessageLocales.KO)), MessageLocales.EN, nz(footer.get(MessageLocales.EN))),
                Map.of(MessageLocales.KO, MailHtml.sanitize(frames.get(MessageLocales.KO)), MessageLocales.EN, MailHtml.sanitize(frames.get(MessageLocales.EN))), null, null);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}

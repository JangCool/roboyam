package com.community.notification.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mail.service.MailDispatcher;
import com.community.notification.channel.ChannelMessageFormatter;
import com.community.notification.channel.ChannelPostResult;
import com.community.notification.channel.NotificationChannelPoster;
import com.community.notification.dto.request.NotificationChannelConfigInput;
import com.community.notification.dto.request.NotificationChannelEnabledRequest;
import com.community.notification.dto.request.NotificationChannelSaveRequest;
import com.community.notification.dto.request.NotificationChannelSecretsInput;
import com.community.notification.dto.response.NotificationChannelConfigView;
import com.community.notification.dto.response.NotificationChannelTestResultView;
import com.community.notification.dto.response.NotificationChannelView;
import com.community.notification.entity.NotificationBuiltinChannelEntity;
import com.community.notification.entity.NotificationChannelEntity;
import com.community.notification.entity.NotificationChannelType;
import com.community.notification.exception.NotificationChannelAlwaysOnException;
import com.community.notification.exception.NotificationChannelBuiltinException;
import com.community.notification.exception.NotificationChannelLimitExceededException;
import com.community.notification.exception.NotificationChannelNameDuplicatedException;
import com.community.notification.exception.NotificationChannelNotFoundException;
import com.community.notification.exception.NotificationChannelNotTestableException;
import com.community.notification.exception.NotificationChannelTypeUnsupportedException;
import com.community.notification.net.OutboundTargetGuard;
import com.community.notification.repository.master.NotificationBuiltinChannelRepository;
import com.community.notification.repository.master.NotificationChannelRepository;
import com.community.notification.repository.master.NotificationDeliveryRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

/**
 * 알림 도구 — 목록·만들기·고치기·켜기/끄기·지우기·연결 시험 (docs/features/notification-contract.md 「알림 도구·조직 구성」).
 *
 * <ul>
 *   <li><b>도구 종류</b>: 사용자가 만드는 것은 Slack · Mattermost 뿐이다. 서랍·이메일·브라우저는 <b>기본 제공</b>이라 코드가 내려주고(id {@code drawer}·{@code email}·{@code browser}, 행 없음)
 *       이름 변경·지우기가 안 되며 사용 여부만 바꾼다(서랍은 늘 켜짐). 이메일은 설정 칸이 없고 설정 &gt; 메일 서버로 보낸다. 카카오·SMS 는 만들 수 없다(400).</li>
 *   <li><b>비밀</b>(봇 토큰·웹훅 주소)은 {@link SecretEncryptor}(AES-256-GCM, {@code ROBOYAM_CRYPTO_KEY})로 암호화해 저장하고 응답·로그·감사·예외에 평문도 암호문도 싣지 않는다 — 응답은
 *       {@code secretKeys}(「입력됨」)뿐이다. 고칠 때 비밀을 안 보내면 저장된 값을 유지하고, 이제 쓰지 않는 비밀(Mattermost 방식·종류에 안 맞는 것)은 지운다. 암호화 키가 없으면 비밀을 저장하는 쓰기는 503.</li>
 *   <li><b>주소 검사</b>: 서버 주소·웹훅 주소는 저장할 때 {@link OutboundTargetGuard} 로 본다(http/https 만, 자격 증명·막힌 주소 거부 — 보낼 때 다시 본다). 주소·토큰이 바뀌면 지난 시험 결과는 지운다.</li>
 *   <li>도구는 최대 {@value #MAX_CHANNELS}개, 이름은 대소문자 무시 유일(1~100자). 도구를 지우면 이벤트의 전달 도구 선택에서도 함께 빠진다(DB 연쇄 삭제).</li>
 *   <li><b>연결 시험</b>(이메일 · Slack · Mattermost): 시험 글/메일을 실제로 보내고 결과(성공/실패 + <b>짧은 사유 코드</b>)와 시각을 도구에 남긴다. 외부 응답 본문은 저장·반환하지 않는다. 실패한 시험은 오류가 아니라 200 {@code ok:false} 다.
 *       이메일은 시험을 누른 운영자 본인에게 한 통 보낸다. 운영자당 분당 횟수 제한이 있다(429). 외부 호출 동안 DB 연결을 잡지 않으려고 클래스 트랜잭션을 걸지 않는다.</li>
 * </ul>
 * 입구의 권한(조회 {@code notification.read}, 변경·시험 {@code notification.manage})은 컨트롤러가 선언한다.
 */
@Service
public class NotificationChannelService {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelService.class);

    static final int MAX_CHANNELS = 20;
    static final int NAME_MAX = 100;
    static final int DESCRIPTION_MAX = 300;
    static final int VALUE_MAX = 300;

    private static final Pattern SLACK_CHANNEL = Pattern.compile("^#?[\\p{L}\\p{N}_.\\-]{1,80}$");
    private static final Pattern MATTERMOST_CHANNEL = Pattern.compile("^[\\p{L}\\p{N}_.\\-]{1,64}$");
    private static final List<String> BUILTIN_KEYS = List.of(NotificationBuiltinChannelEntity.DRAWER, NotificationBuiltinChannelEntity.EMAIL, NotificationBuiltinChannelEntity.BROWSER);

    private final NotificationChannelRepository channels;
    private final NotificationBuiltinChannelRepository builtins;
    private final NotificationDeliveryRepository delivery;
    private final SecretEncryptor encryptor;
    private final OutboundTargetGuard guard;
    private final NotificationChannelPoster poster;
    private final MailDispatcher mail;
    private final ChannelTestRateLimiter limiter;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final Messages messages;
    private final TransactionTemplate tx;

    public NotificationChannelService(NotificationChannelRepository channels, NotificationBuiltinChannelRepository builtins, NotificationDeliveryRepository delivery,
                                      SecretEncryptor encryptor, OutboundTargetGuard guard, NotificationChannelPoster poster, MailDispatcher mail, ChannelTestRateLimiter limiter,
                                      SnowflakeIdGenerator ids, AuditLogService audit, Messages messages, PlatformTransactionManager txManager) {
        this.channels = channels;
        this.builtins = builtins;
        this.delivery = delivery;
        this.encryptor = encryptor;
        this.guard = guard;
        this.poster = poster;
        this.mail = mail;
        this.limiter = limiter;
        this.ids = ids;
        this.audit = audit;
        this.messages = messages;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    /** 기본 제공 도구(서랍 · 이메일 · 브라우저) 먼저, 그다음 만든 차례 */
    @Transactional(readOnly = true)
    public List<NotificationChannelView> list() {
        Map<Long, Integer> used = delivery.eventCountByChannel();
        List<NotificationChannelView> out = new ArrayList<>();
        for (String key : BUILTIN_KEYS) {
            out.add(builtinView(key, stateOf(key)));
        }
        for (NotificationChannelEntity channel : channels.findAllByOrderByIdAsc()) {
            out.add(customView(channel, used.getOrDefault(channel.getId(), 0)));
        }
        return out;
    }

    // ── 쓰기 ────────────────────────────────────────────────────────────────

    @Transactional
    public NotificationChannelView create(AdminActor actor, NotificationChannelSaveRequest request) {
        NotificationChannelType type = createType(request == null ? null : request.type());
        if (channels.count() >= MAX_CHANNELS) {
            throw new NotificationChannelLimitExceededException(MAX_CHANNELS);
        }
        Resolved resolved = resolve(type, request, null);
        if (channels.existsByChannelNameIgnoreCase(resolved.name())) {
            throw new NotificationChannelNameDuplicatedException();
        }
        Instant now = Instant.now();
        NotificationChannelEntity channel = NotificationChannelEntity.create(ids.nextId(), type, resolved.name(), resolved.description(), resolved.enabled(), resolved.target(),
                resolved.mode(), resolved.serverUrl(), resolved.botTokenEnc(), resolved.webhookUrlEnc(), actor.operatorId(), now);
        channels.persist(channel);
        flushOrDuplicated();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("type", type.name());
        after.put("name", channel.getChannelName());
        after.put("enabled", channel.isEnabled());
        if (channel.getChannelTarget() != null) {
            after.put("channel", channel.getChannelTarget());
        }
        if (channel.getMattermostMode() != null) {
            after.put("mattermostMode", channel.getMattermostMode());
        }
        AuditEvent event = event(actor, AuditActions.NOTIFICATION_CHANNEL_CREATE, channel.getId().toString()).with("before", Map.of()).with("after", after);
        if (!resolved.secretsChanged().isEmpty()) {
            event = event.with("secretsSet", List.copyOf(resolved.secretsChanged())); // 어떤 칸이 입력됐는지 이름만 — 값은 남기지 않는다
        }
        audit.record(event);
        log.info("알림 도구를 만들었다 channelId={} type={} by operatorId={}", channel.getId(), type, actor.operatorId());
        return customView(channel, 0);
    }

    @Transactional
    public NotificationChannelView update(AdminActor actor, String channelId, NotificationChannelSaveRequest request) {
        String key = builtinKey(channelId);
        if (key != null) {
            return updateBuiltin(actor, key, request);
        }
        NotificationChannelEntity channel = lock(channelId);
        requireSameType(request == null ? null : request.type(), channel.getChannelType().name());
        Resolved resolved = resolve(channel.getChannelType(), request, channel);
        if (channels.existsByChannelNameIgnoreCaseAndIdNot(resolved.name(), channel.getId())) {
            throw new NotificationChannelNameDuplicatedException();
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        diff(before, after, "name", channel.getChannelName(), resolved.name());
        diff(before, after, "description", channel.getDescription() == null ? "" : channel.getDescription(), resolved.description() == null ? "" : resolved.description());
        diff(before, after, "enabled", channel.isEnabled(), resolved.enabled());
        diff(before, after, "channel", channel.getChannelTarget(), resolved.target());
        diff(before, after, "mattermostMode", channel.getMattermostMode(), resolved.mode());
        boolean connectionChanged = !resolved.secretsChanged().isEmpty() || !Objects.equals(channel.getChannelTarget(), resolved.target())
                || !Objects.equals(channel.getMattermostMode(), resolved.mode());
        if (!after.isEmpty() || connectionChanged) {
            Instant now = Instant.now();
            channel.change(resolved.name(), resolved.description(), resolved.enabled(), resolved.target(), resolved.mode(), resolved.serverUrl(), resolved.botTokenEnc(),
                    resolved.webhookUrlEnc(), actor.operatorId(), now);
            if (connectionChanged) {
                channel.clearTest(); // 접속 정보가 바뀌었다 — 지난 시험 결과는 더는 맞지 않는다
            }
            flushOrDuplicated();
            AuditEvent event = event(actor, AuditActions.NOTIFICATION_CHANNEL_UPDATE, channel.getId().toString()).with("before", before).with("after", after);
            if (!resolved.secretsChanged().isEmpty()) {
                event = event.with("secretsSet", List.copyOf(resolved.secretsChanged()));
            }
            audit.record(event);
            log.info("알림 도구를 고쳤다 channelId={} by operatorId={}", channel.getId(), actor.operatorId());
        }
        return customView(channel, delivery.eventCountByChannel().getOrDefault(channel.getId(), 0));
    }

    @Transactional
    public NotificationChannelView setEnabled(AdminActor actor, String channelId, NotificationChannelEnabledRequest request) {
        if (request == null || request.enabled() == null) {
            throw ValidationException.of("enabled", new MessageRef("notification.channel.enabled.required"));
        }
        boolean enabled = request.enabled();
        String key = builtinKey(channelId);
        if (key != null) {
            return toggleBuiltin(actor, key, enabled);
        }
        NotificationChannelEntity channel = lock(channelId);
        if (channel.isEnabled() != enabled) {
            channel.toggle(enabled, actor.operatorId(), Instant.now());
            audit.record(event(actor, AuditActions.NOTIFICATION_CHANNEL_TOGGLE, channel.getId().toString()).with("before", Map.of("enabled", !enabled)).with("after", Map.of("enabled", enabled)));
            log.info("알림 도구 사용 여부를 바꿨다 channelId={} enabled={} by operatorId={}", channel.getId(), enabled, actor.operatorId());
        }
        return customView(channel, delivery.eventCountByChannel().getOrDefault(channel.getId(), 0));
    }

    @Transactional
    public void delete(AdminActor actor, String channelId) {
        if (builtinKey(channelId) != null) {
            throw new NotificationChannelBuiltinException();
        }
        NotificationChannelEntity channel = lock(channelId);
        String name = channel.getChannelName();
        String type = channel.getChannelType().name();
        channels.delete(channel);
        channels.flush();
        audit.record(event(actor, AuditActions.NOTIFICATION_CHANNEL_DELETE, channel.getId().toString()).with("deleted", List.of(type + ":" + name)));
        log.info("알림 도구를 지웠다 channelId={} by operatorId={}", channel.getId(), actor.operatorId());
    }

    // ── 연결 시험 ──────────────────────────────────────────────────────────

    /**
     * 연결을 시험한다. 시험이 실패한 것은 오류가 아니라 {@code ok=false} 응답이다. 서랍·브라우저는 400 {@code NOTIFICATION_CHANNEL_NOT_TESTABLE}.
     * 트랜잭션을 걸지 않는다 — 외부 서비스를 기다리는 동안 DB 연결을 잡지 않는다(결과 기록만 짧은 트랜잭션).
     */
    public NotificationChannelTestResultView test(AdminActor actor, String channelId) {
        String key = builtinKey(channelId);
        NotificationChannelEntity channel = null;
        if (key != null) {
            if (!key.equals(NotificationBuiltinChannelEntity.EMAIL)) {
                throw new NotificationChannelNotTestableException();
            }
        } else {
            Long id = parseId(channelId);
            channel = id == null ? null : channels.findById(id).orElse(null);
            if (channel == null) {
                throw new NotificationChannelNotFoundException();
            }
        }
        limiter.acquire(actor.operatorId()); // 실제로 외부에 요청을 보내는 시험만 센다
        ChannelPostResult result;
        if (key != null) {
            result = sendTestMail(actor);
        } else {
            String text = ChannelMessageFormatter.testMessage(channel.getChannelType(), messages.get(new MessageRef("notification.channel.testMessage")));
            result = poster.post(channel, text);
        }
        Instant now = Instant.now();
        String targetId = key != null ? key : channelId.strip();
        String type = key != null ? "EMAIL" : channel.getChannelType().name();
        tx.executeWithoutResult(status -> {
            if (key != null) {
                builtins.findWithLockByChannelKey(key).orElseGet(() -> createBuiltin(key, now)).recordTest(result.ok(), result.code(), now);
            } else {
                channels.findWithLockById(Long.parseLong(targetId)).ifPresent(found -> found.recordTest(result.ok(), result.code(), now));
            }
            audit.record(event(actor, AuditActions.NOTIFICATION_CHANNEL_TEST, targetId).with("type", type).with("ok", result.ok()).with("code", result.code()));
        });
        log.info("알림 도구 연결을 시험했다 channel={} 결과={} by operatorId={}", targetId, result.code(), actor.operatorId());
        return new NotificationChannelTestResultView(result.ok(), resultMessage(result.code()), now);
    }

    private ChannelPostResult sendTestMail(AdminActor actor) {
        try {
            String subject = messages.get(new MessageRef("notification.channel.test.mail.subject"));
            String body = messages.get(new MessageRef("notification.channel.test.mail.body"));
            boolean sent = mail.send(actor.email(), subject, "<p style=\"font-size:15px;line-height:1.6\">" + HtmlUtils.htmlEscape(body) + "</p>");
            return sent ? new ChannelPostResult(true, ChannelPostResult.MAIL_OK) : ChannelPostResult.failure(ChannelPostResult.MAIL_NOT_CONFIGURED);
        } catch (RuntimeException e) {
            // 예외 문구에 수신자 주소·서버 응답이 실릴 수 있어 클래스 이름만 남긴다
            log.warn("이메일 도구 시험 메일을 보내지 못했다 원인={}", e.getClass().getSimpleName());
            return ChannelPostResult.failure(ChannelPostResult.MAIL_FAILED);
        }
    }

    // ── 기본 제공 도구 ─────────────────────────────────────────────────────

    private NotificationChannelView updateBuiltin(AdminActor actor, String key, NotificationChannelSaveRequest request) {
        requireSameType(request == null ? null : request.type(), key.toUpperCase(Locale.ROOT));
        if (request.enabled() == null) {
            return builtinView(key, stateOf(key));
        }
        return toggleBuiltin(actor, key, request.enabled());
    }

    private NotificationChannelView toggleBuiltin(AdminActor actor, String key, boolean enabled) {
        if (key.equals(NotificationBuiltinChannelEntity.DRAWER) && !enabled) {
            throw new NotificationChannelAlwaysOnException();
        }
        if (key.equals(NotificationBuiltinChannelEntity.DRAWER)) {
            return builtinView(key, null);
        }
        Instant now = Instant.now();
        NotificationBuiltinChannelEntity state = builtins.findWithLockByChannelKey(key).orElseGet(() -> createBuiltin(key, now));
        if (state.isEnabled() != enabled) {
            state.toggle(enabled, actor.operatorId(), now);
            audit.record(event(actor, AuditActions.NOTIFICATION_CHANNEL_TOGGLE, key).with("before", Map.of("enabled", !enabled)).with("after", Map.of("enabled", enabled)));
            log.info("기본 제공 알림 도구 사용 여부를 바꿨다 channel={} enabled={} by operatorId={}", key, enabled, actor.operatorId());
        }
        return builtinView(key, state);
    }

    private NotificationBuiltinChannelEntity createBuiltin(String key, Instant now) {
        NotificationBuiltinChannelEntity state = NotificationBuiltinChannelEntity.create(key, now);
        builtins.persist(state);
        return state;
    }

    private NotificationBuiltinChannelEntity stateOf(String key) {
        return key.equals(NotificationBuiltinChannelEntity.DRAWER) ? null : builtins.findById(key).orElse(null);
    }

    // ── 입력 검사 ──────────────────────────────────────────────────────────

    /** 검사를 마치고 저장할 모양으로 다듬은 값. 비밀은 암호문이다 */
    private record Resolved(String name, String description, boolean enabled, String target, String mode, String serverUrl, String botTokenEnc, String webhookUrlEnc,
                            Set<String> secretsChanged) {
    }

    private Resolved resolve(NotificationChannelType type, NotificationChannelSaveRequest request, NotificationChannelEntity existing) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String name = request == null || request.name() == null ? "" : request.name().strip();
        if (name.isEmpty()) {
            invalid.put("name", new MessageRef("notification.channel.name.required"));
        } else if (name.length() > NAME_MAX) {
            invalid.put("name", new MessageRef("notification.channel.name.max", String.valueOf(NAME_MAX)));
        } else if (name.chars().anyMatch(Character::isISOControl)) {
            invalid.put("name", new MessageRef("notification.channel.name.control"));
        }
        String description = request == null || request.description() == null ? null : request.description().strip();
        if (description != null && description.isEmpty()) {
            description = null;
        }
        if (description != null && description.length() > DESCRIPTION_MAX) {
            invalid.put("description", new MessageRef("notification.channel.description.max", String.valueOf(DESCRIPTION_MAX)));
        } else if (description != null && description.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            invalid.put("description", new MessageRef("notification.channel.description.control"));
        }
        boolean enabled = request == null || request.enabled() == null ? (existing == null || existing.isEnabled()) : request.enabled();

        NotificationChannelConfigInput config = request == null || request.config() == null ? new NotificationChannelConfigInput(null, null, null) : request.config();
        NotificationChannelSecretsInput secrets = request == null || request.secrets() == null ? new NotificationChannelSecretsInput(null, null) : request.secrets();
        String newToken = blankToNull(secrets.botToken());
        String newWebhook = blankToNull(secrets.webhookUrl());
        String target = null;
        String mode = null;
        String serverUrl = null;
        boolean usesToken;
        boolean usesWebhook = false;

        if (type == NotificationChannelType.SLACK) {
            usesToken = true;
            target = blankToNull(config.channel());
            checkChannel(invalid, target, SLACK_CHANNEL);
        } else {
            mode = blankToNull(config.mattermostMode()) == null ? "bot" : config.mattermostMode().strip().toLowerCase(Locale.ROOT);
            if (!mode.equals("webhook") && !mode.equals("bot")) {
                invalid.put("mattermostMode", new MessageRef("notification.channel.mode.invalid"));
                mode = "bot";
            }
            if (mode.equals("webhook")) {
                usesToken = false;
                usesWebhook = true;
            } else {
                usesToken = true;
                serverUrl = blankToNull(config.serverUrl());
                if (serverUrl == null) {
                    invalid.put("serverUrl", new MessageRef("notification.channel.serverUrl.required"));
                } else {
                    serverUrl = serverUrl.replaceAll("/+$", "");
                    checkUrl(invalid, "serverUrl", serverUrl, true);
                }
                target = blankToNull(config.channel());
                checkChannel(invalid, target, MATTERMOST_CHANNEL);
            }
        }

        if (usesToken) {
            if (newToken == null && (existing == null || existing.getBotTokenEnc() == null)) {
                invalid.put("botToken", new MessageRef("notification.channel.botToken.required"));
            } else if (newToken != null) {
                checkSecretText(invalid, "botToken", newToken);
            }
        }
        if (usesWebhook) {
            if (newWebhook == null && (existing == null || existing.getWebhookUrlEnc() == null)) {
                invalid.put("webhookUrl", new MessageRef("notification.channel.webhookUrl.required"));
            } else if (newWebhook != null) {
                checkUrl(invalid, "webhookUrl", newWebhook, false);
            }
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }

        Set<String> changed = new LinkedHashSet<>();
        String botTokenEnc = null;
        String webhookUrlEnc = null;
        if (usesToken) {
            botTokenEnc = newToken != null ? encrypt(newToken) : existing.getBotTokenEnc();
            if (newToken != null) {
                changed.add("botToken");
            }
        }
        if (usesWebhook) {
            webhookUrlEnc = newWebhook != null ? encrypt(newWebhook) : existing.getWebhookUrlEnc();
            if (newWebhook != null) {
                changed.add("webhookUrl");
            }
        }
        if (serverUrl != null && (existing == null || !serverUrl.equals(existing.getServerUrl()))) {
            changed.add("serverUrl");
        }
        return new Resolved(name, description, enabled, target, mode, serverUrl, botTokenEnc, webhookUrlEnc, changed);
    }

    private String encrypt(String plain) {
        return encryptor.encrypt(plain); // 키가 없으면 503 CRYPTO_KEY_MISSING
    }

    private void checkChannel(Map<String, MessageRef> invalid, String target, Pattern pattern) {
        if (target == null) {
            invalid.put("channel", new MessageRef("notification.channel.channel.required"));
        } else if (target.length() > VALUE_MAX) {
            invalid.put("channel", new MessageRef("notification.channel.value.max", String.valueOf(VALUE_MAX)));
        } else if (!pattern.matcher(target).matches()) {
            invalid.put("channel", new MessageRef("notification.channel.channel.format"));
        }
    }

    private void checkSecretText(Map<String, MessageRef> invalid, String field, String value) {
        if (value.length() > VALUE_MAX) {
            invalid.put(field, new MessageRef("notification.channel.value.max", String.valueOf(VALUE_MAX)));
        } else if (value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            invalid.put(field, new MessageRef("notification.channel.secret.format"));
        }
    }

    private void checkUrl(Map<String, MessageRef> invalid, String field, String url, boolean baseOnly) {
        if (url.length() > VALUE_MAX) {
            invalid.put(field, new MessageRef("notification.channel.value.max", String.valueOf(VALUE_MAX)));
            return;
        }
        OutboundTargetGuard.Reason reason = guard.validate(url);
        if (reason != null) {
            invalid.put(field, new MessageRef("notification.channel.url." + reason.name().toLowerCase(Locale.ROOT)));
            return;
        }
        if (baseOnly) {
            try {
                URI uri = new URI(url);
                if (uri.getRawQuery() != null) { // 서버 주소는 뿌리 주소다 — 질의 문자열을 받지 않는다
                    invalid.put(field, new MessageRef("notification.channel.url.format"));
                }
            } catch (URISyntaxException e) {
                invalid.put(field, new MessageRef("notification.channel.url.format"));
            }
        }
    }

    private NotificationChannelType createType(String raw) {
        String text = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw ValidationException.of("type", new MessageRef("notification.channel.type.required"));
        }
        return switch (text) {
            case "SLACK" -> NotificationChannelType.SLACK;
            case "MATTERMOST" -> NotificationChannelType.MATTERMOST;
            case "KAKAO", "SMS" -> throw new NotificationChannelTypeUnsupportedException();
            case "DRAWER", "EMAIL", "BROWSER" -> throw new NotificationChannelBuiltinException();
            default -> throw ValidationException.of("type", new MessageRef("notification.channel.type.invalid"));
        };
    }

    private void requireSameType(String requested, String actual) {
        if (requested == null || !requested.strip().toUpperCase(Locale.ROOT).equals(actual)) {
            throw ValidationException.of("type", new MessageRef("notification.channel.type.immutable"));
        }
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static String builtinKey(String channelId) {
        String key = channelId == null ? "" : channelId.strip().toLowerCase(Locale.ROOT);
        return BUILTIN_KEYS.contains(key) ? key : null;
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private NotificationChannelEntity lock(String channelId) {
        Long id = parseId(channelId);
        if (id == null) {
            throw new NotificationChannelNotFoundException();
        }
        return channels.findWithLockById(id).orElseThrow(NotificationChannelNotFoundException::new);
    }

    private void flushOrDuplicated() {
        try {
            channels.flush();
        } catch (DataIntegrityViolationException e) {
            throw new NotificationChannelNameDuplicatedException();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void diff(Map<String, Object> before, Map<String, Object> after, String field, Object was, Object now) {
        if (!Objects.equals(was, now)) {
            before.put(field, was == null ? "" : was);
            after.put(field, now == null ? "" : now);
        }
    }

    private static AuditEvent event(AdminActor actor, String action, String channelId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("NOTIFICATION_CHANNEL", channelId);
    }

    // ── 응답 ────────────────────────────────────────────────────────────────

    private NotificationChannelView builtinView(String key, NotificationBuiltinChannelEntity state) {
        String type = key.toUpperCase(Locale.ROOT);
        String name = messages.get(new MessageRef("notification.channel.builtin." + key + ".name"));
        boolean enabled = key.equals(NotificationBuiltinChannelEntity.DRAWER) || state == null || state.isEnabled();
        int used = switch (key) {
            case NotificationBuiltinChannelEntity.EMAIL -> delivery.emailEventCount();
            case NotificationBuiltinChannelEntity.BROWSER -> delivery.browserEventCount();
            default -> 0;
        };
        NotificationChannelTestResultView lastTest = state == null || state.getLastTestAt() == null ? null : testView(state.getLastTestOk(), state.getLastTestCode(), state.getLastTestAt());
        return new NotificationChannelView(key, type, name, null, enabled, true, NotificationChannelConfigView.empty(), List.of(), lastTest, used);
    }

    private NotificationChannelView customView(NotificationChannelEntity channel, int used) {
        List<String> secretKeys = new ArrayList<>();
        if (channel.getBotTokenEnc() != null) {
            secretKeys.add("botToken");
        }
        if (channel.getWebhookUrlEnc() != null) {
            secretKeys.add("webhookUrl");
        }
        NotificationChannelTestResultView lastTest = channel.getLastTestAt() == null ? null : testView(channel.getLastTestOk(), channel.getLastTestCode(), channel.getLastTestAt());
        return new NotificationChannelView(channel.getId().toString(), channel.getChannelType().name(), channel.getChannelName(), channel.getDescription(), channel.isEnabled(), false,
                new NotificationChannelConfigView(channel.getChannelTarget(), channel.getMattermostMode(), channel.getServerUrl()), secretKeys, lastTest, used);
    }

    private NotificationChannelTestResultView testView(Boolean ok, String code, Instant at) {
        return new NotificationChannelTestResultView(Boolean.TRUE.equals(ok), resultMessage(code), at);
    }

    private String resultMessage(String code) {
        String safe = code != null && ChannelPostResult.CODES.contains(code) ? code : ChannelPostResult.ERROR;
        return messages.get(new MessageRef("notification.channel.test." + safe));
    }
}

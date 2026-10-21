package com.community.notification.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.dto.request.NotificationEventUpdateRequest;
import com.community.notification.dto.response.NotificationEventView;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationEventEntity;
import com.community.notification.exception.NotificationEventNotFoundException;
import com.community.notification.exception.NotificationEventNotReadyException;
import com.community.notification.exception.NotificationMandatoryException;
import com.community.notification.repository.master.AdminNotificationRepositoryQDSL;
import com.community.notification.repository.master.NotificationEventRepository;
import com.community.notification.service.NotificationEventSettings.State;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 관리 — 이벤트마다 <b>사용 여부와 이메일 전달</b>만 바꾼다(앱 안 서랍 전달은 늘 켜짐). 수신 규칙·심각도·필수 여부는 코드에 고정이다(화면에서 바꾸지 않는다 — 권한 상승 방지).
 *
 * <ul>
 *   <li><b>검사 순서</b>: 없는 이벤트(404 {@code NOTIFICATION_EVENT_NOT_FOUND}) → 입력 형식(400 {@code fields.enabled|emailEnabled}) → 준비 중 이벤트(409
 *       {@code NOTIFICATION_EVENT_NOT_READY}) → 필수 이벤트를 끄려 함(409 {@code NOTIFICATION_MANDATORY}) → 이메일을 보낼 수 없는 이벤트의 이메일을 켜려 함(400 {@code fields.emailEnabled}).</li>
 *   <li>같은 이벤트를 동시에 바꾸는 요청은 행 잠금으로 줄 세운다. 바뀐 때만 감사 {@code NOTIFICATION_EVENT_UPDATE}(전·후 값)를 남긴다.</li>
 *   <li>목록의 「최근 30일 발송 수」는 그 기간에 만들어진 알림 행 수다(받은 사람마다 한 건). 읽은 알림은 30일 뒤 정리 배치가 지우므로 아주 오래된 값은 아니다.</li>
 * </ul>
 * 입구의 권한(조회 {@code notification.read}, 변경 {@code notification.manage})은 컨트롤러가 선언한다.
 */
@Service
public class NotificationEventAdminService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventAdminService.class);

    private static final Duration SENT_WINDOW = Duration.ofDays(30);

    private final NotificationEventRepository events;
    private final NotificationEventSettings settings;
    private final AdminNotificationRepositoryQDSL notifications;
    private final AuditLogService audit;
    private final Messages messages;

    public NotificationEventAdminService(NotificationEventRepository events, NotificationEventSettings settings, AdminNotificationRepositoryQDSL notifications,
                                         AuditLogService audit, Messages messages) {
        this.events = events;
        this.settings = settings;
        this.notifications = notifications;
        this.audit = audit;
        this.messages = messages;
    }

    /** 모든 이벤트(준비 중 포함) — 영역·코드순 */
    @Transactional(readOnly = true)
    public List<NotificationEventView> list() {
        Map<String, State> states = settings.all();
        Map<String, Long> sent = notifications.countByEventSince(Instant.now().minus(SENT_WINDOW));
        Locale locale = messages.locale();
        return Arrays.stream(AdminNotificationEvent.values()).sorted(NotificationVisibility.ORDER)
                .map(event -> view(event, states.get(event.code()), sent.getOrDefault(event.code(), 0L), locale)).toList();
    }

    @Transactional
    public NotificationEventView update(AdminActor actor, String code, NotificationEventUpdateRequest request) {
        AdminNotificationEvent event = AdminNotificationEvent.ofCode(code).orElseThrow(NotificationEventNotFoundException::new);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (request == null || request.enabled() == null) {
            invalid.put("enabled", new MessageRef("notification.event.required"));
        }
        if (request == null || request.emailEnabled() == null) {
            invalid.put("emailEnabled", new MessageRef("notification.event.required"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        boolean enabled = request.enabled();
        boolean emailEnabled = request.emailEnabled();
        if (!event.featureReady()) {
            throw new NotificationEventNotReadyException();
        }
        if (event.mandatory() && !enabled) {
            throw new NotificationMandatoryException();
        }
        if (!event.emailable() && emailEnabled) {
            throw ValidationException.of("emailEnabled", new MessageRef("notification.event.emailNotAllowed"));
        }

        Instant now = Instant.now();
        NotificationEventEntity row = events.findWithLockByCode(code).orElseGet(() -> { // 맞춤 전에 불린 드문 경우 — 처음 값으로 만든다
            NotificationEventEntity created = NotificationEventEntity.of(event, now);
            events.persist(created);
            return created;
        });
        boolean wasEnabled = row.isEnabled();
        boolean wasEmailEnabled = row.isEmailEnabled();
        if (wasEnabled != enabled || wasEmailEnabled != emailEnabled) {
            row.change(enabled, emailEnabled, actor.operatorId(), now);
            Map<String, Object> before = new LinkedHashMap<>(); // 바뀐 값만 남긴다(그대로인 칸까지 「꺼짐 → 꺼짐」으로 찍히지 않게)
            Map<String, Object> after = new LinkedHashMap<>();
            if (wasEnabled != enabled) {
                before.put("enabled", wasEnabled);
                after.put("enabled", enabled);
            }
            if (wasEmailEnabled != emailEnabled) {
                before.put("emailEnabled", wasEmailEnabled);
                after.put("emailEnabled", emailEnabled);
            }
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NOTIFICATION_EVENT_UPDATE, AuditResult.SUCCESS).target("NOTIFICATION_EVENT", code)
                    .with("before", before).with("after", after));
            log.info("알림 이벤트 설정을 바꿨다 event={} enabled={} emailEnabled={} by operatorId={}", code, enabled, emailEnabled, actor.operatorId());
        }
        long sent = notifications.countByEventSince(now.minus(SENT_WINDOW)).getOrDefault(code, 0L);
        return view(event, new State(enabled, emailEnabled), sent, messages.locale());
    }

    private NotificationEventView view(AdminNotificationEvent event, State state, long sent, Locale locale) {
        return new NotificationEventView(event.code(), event.area().id(), event.severity().kind(), messages.get(new MessageRef(event.nameKey()), locale),
                messages.get(new MessageRef(event.descriptionKey()), locale), messages.get(new MessageRef(event.recipientsKey()), locale), event.mandatory(),
                event.emailable(), state.enabled(), state.emailEnabled(), event.featureReady(), sent);
    }
}

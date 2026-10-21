package com.community.notification.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.dto.request.NotificationEventMuteRequest;
import com.community.notification.dto.response.MyNotificationEventView;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.OperatorNotificationMuteEntity;
import com.community.notification.exception.NotificationEventNotFoundException;
import com.community.notification.exception.NotificationMandatoryException;
import com.community.notification.repository.master.OperatorNotificationMuteRepository;
import com.community.notification.service.NotificationEventSettings.State;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 설정 중 <b>이벤트별 끄기</b> — 내가 볼 수 있는(권한이 있는) 이벤트마다 끄거나 켠다. 준비 중인 이벤트와 내가 볼 수 없는 이벤트는 목록에 없고 바꾸려 하면 404
 * {@code NOTIFICATION_EVENT_NOT_FOUND}(있는지 여부를 알리지 않는다). <b>필수 이벤트(보안)는 끌 수 없다</b>(409 {@code NOTIFICATION_MANDATORY}). 끄면 그 이벤트의 알림이 새로 만들어지지 않고
 * 이미 쌓인 것도 서랍·안 읽은 수에서 빠진다. 이메일 수신 여부는 기존 {@code MyNotificationService}(알림 설정) 다. 바뀐 때만 감사 {@code PROFILE_NOTIFICATION_UPDATE}.
 */
@Service
public class MyNotificationEventService {

    private static final Logger log = LoggerFactory.getLogger(MyNotificationEventService.class);

    private final OperatorNotificationMuteRepository mutes;
    private final OperatorRepository operators;
    private final NotificationVisibility visibility;
    private final NotificationEventSettings settings;
    private final AuditLogService audit;
    private final Messages messages;

    public MyNotificationEventService(OperatorNotificationMuteRepository mutes, OperatorRepository operators, NotificationVisibility visibility,
                                      NotificationEventSettings settings, AuditLogService audit, Messages messages) {
        this.mutes = mutes;
        this.operators = operators;
        this.visibility = visibility;
        this.settings = settings;
        this.audit = audit;
        this.messages = messages;
    }

    /** 내가 볼 수 있는 이벤트와 내가 껐는지 — 영역·코드순 */
    @Transactional(readOnly = true)
    public List<MyNotificationEventView> list(AdminActor actor) {
        Set<String> muted = visibility.mutedCodes(actor.operatorId());
        Map<String, State> states = settings.all();
        Locale locale = messages.locale();
        return AdminNotificationEvent.visibleTo(actor::can).stream().sorted(NotificationVisibility.ORDER)
                .map(event -> view(event, !event.mandatory() && muted.contains(event.code()), states.get(event.code()), locale)).toList();
    }

    @Transactional
    public MyNotificationEventView setMuted(AdminActor actor, String code, NotificationEventMuteRequest request) {
        AdminNotificationEvent event = AdminNotificationEvent.ofCode(code).filter(AdminNotificationEvent::featureReady).filter(e -> e.isVisibleTo(actor::can))
                .orElseThrow(NotificationEventNotFoundException::new);
        if (request == null || request.muted() == null) {
            throw ValidationException.of("muted", new MessageRef("notification.muted.required"));
        }
        boolean muted = request.muted();
        if (muted && event.mandatory()) {
            throw new NotificationMandatoryException();
        }

        operators.findWithLockById(actor.operatorId()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED)); // 같은 운영자의 동시 변경을 줄 세운다
        boolean was = visibility.mutedCodes(actor.operatorId()).contains(code);
        if (was != muted) {
            if (muted) {
                mutes.persist(OperatorNotificationMuteEntity.create(actor.operatorId(), code, Instant.now()));
            } else {
                mutes.deleteByOperatorIdAndEventCode(actor.operatorId(), code);
            }
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.PROFILE_NOTIFICATION_UPDATE, AuditResult.SUCCESS)
                    .target("OPERATOR", Long.toString(actor.operatorId())).with("event", code).with("before", Map.of("muted", was)).with("after", Map.of("muted", muted)));
            log.info("내 알림 이벤트를 {} operatorId={} event={}", muted ? "껐다" : "켰다", actor.operatorId(), code);
        }
        return view(event, muted, settings.of(event), messages.locale());
    }

    private MyNotificationEventView view(AdminNotificationEvent event, boolean muted, State state, Locale locale) {
        return new MyNotificationEventView(event.code(), event.area().id(), messages.get(new MessageRef(event.nameKey()), locale),
                messages.get(new MessageRef(event.descriptionKey()), locale), event.severity().kind(), event.mandatory(), muted, state.emailEnabled());
    }
}

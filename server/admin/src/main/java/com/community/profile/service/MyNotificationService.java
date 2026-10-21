package com.community.profile.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.entity.OperatorPreferenceEntity;
import com.community.operator.repository.master.OperatorPreferenceRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.profile.dto.request.MyNotificationRequest;
import com.community.profile.dto.response.MyNotificationResponse;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 수신 설정 — 관리자 알림을 이메일로 받을지(처음 값 켜짐). 알림 메일({@code NotificationEmailSender})이 이 값을 본다 — 초대·재설정·승인번호 같은 시스템 메일은 이 설정과 상관없이 나간다.
 * 이벤트별 끄기는 {@code MyNotificationEventService} 다.
 * 행이 없으면 처음 값으로 읽고, 처음 저장할 때 운영자 행을 잠근 뒤 설정 행을 만든다. 바뀐 때만 감사 {@code PROFILE_NOTIFICATION_UPDATE}.
 */
@Service
public class MyNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MyNotificationService.class);

    private final OperatorPreferenceRepository preferences;
    private final OperatorRepository operators;
    private final AuditLogService audit;

    public MyNotificationService(OperatorPreferenceRepository preferences, OperatorRepository operators, AuditLogService audit) {
        this.preferences = preferences;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MyNotificationResponse get(AdminActor actor) {
        return new MyNotificationResponse(preferences.findById(actor.operatorId()).map(OperatorPreferenceEntity::isEmailNotification).orElse(true));
    }

    @Transactional
    public MyNotificationResponse save(AdminActor actor, MyNotificationRequest request) {
        if (request == null || request.emailEnabled() == null) {
            throw ValidationException.of("emailEnabled", new MessageRef("profile.notification.required"));
        }
        boolean enabled = request.emailEnabled();

        operators.findWithLockById(actor.operatorId()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Instant now = Instant.now();
        OperatorPreferenceEntity preference = preferences.findById(actor.operatorId()).orElse(null);
        boolean was = preference == null || preference.isEmailNotification();
        if (preference == null) {
            preference = OperatorPreferenceEntity.create(actor.operatorId(), now);
            preferences.persist(preference);
        }
        if (was != enabled) {
            preference.changeEmailNotification(enabled, now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.PROFILE_NOTIFICATION_UPDATE, AuditResult.SUCCESS)
                    .target("OPERATOR", Long.toString(actor.operatorId())).with("before", Map.of("emailEnabled", was)).with("after", Map.of("emailEnabled", enabled)));
            log.info("내 알림 설정을 바꿨다 operatorId={} emailEnabled={}", actor.operatorId(), enabled);
        }
        return new MyNotificationResponse(enabled);
    }
}

package com.community.notification.service;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationEventEntity;
import com.community.notification.repository.master.NotificationEventRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트마다 <b>지금 적용되는</b> 사용 여부·이메일 전달 — 운영자가 이벤트 관리 화면에서 바꾼 값({@code notification_events})에 코드의 불변 규칙을 겹친 값이다:
 * 필수 이벤트는 항상 켜져 있고, 이메일을 보낼 수 없는 이벤트({@code emailable=false})는 이메일이 항상 꺼져 있다. 표에 행이 아직 없으면(맞춤 전) 코드의 처음 값이다.
 * 발행할 때마다 읽는다(스물몇 줄이고 설정은 바로 반영돼야 한다 — 서버가 여러 대여도 다른 서버의 변경을 바로 따른다).
 */
@Component
public class NotificationEventSettings {

    /**
     * 이벤트 하나에 적용되는 값 — {@code browserEnabled} 는 브라우저 알림 전달 선택(서버가 하는 일은 저장뿐이다), {@code defaultRecipients} 는 기본 수신 규칙으로도 보내는가
     * (false = 고른 추가 수신 팀에만 — 본인 전용·필수 이벤트는 늘 true)
     */
    public record State(boolean enabled, boolean emailEnabled, boolean browserEnabled, boolean defaultRecipients) {
    }

    private final NotificationEventRepository events;

    public NotificationEventSettings(NotificationEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public State of(AdminNotificationEvent event) {
        return effective(event, events.findById(event.code()).orElse(null));
    }

    /** 모든 이벤트의 값 — 코드 → 값 */
    @Transactional(readOnly = true)
    public Map<String, State> all() {
        Map<String, NotificationEventEntity> rows = new HashMap<>();
        events.findAll().forEach(row -> rows.put(row.getCode(), row));
        Map<String, State> states = new HashMap<>();
        for (AdminNotificationEvent event : AdminNotificationEvent.values()) {
            states.put(event.code(), effective(event, rows.get(event.code())));
        }
        return states;
    }

    private static State effective(AdminNotificationEvent event, NotificationEventEntity row) {
        boolean enabled = event.mandatory() || (row == null ? event.defaultEnabled() : row.isEnabled());
        boolean emailEnabled = event.emailable() && (row == null ? event.defaultEmailEnabled() : row.isEmailEnabled());
        boolean defaultRecipients = !event.broadcastAllowed() || row == null || row.isDefaultRecipients();
        return new State(enabled, emailEnabled, row != null && row.isBrowserEnabled(), defaultRecipients);
    }
}

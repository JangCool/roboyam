package com.community.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 이벤트 설정 한 줄 — V40__admin_notifications.sql 의 {@code notification_events}. 이벤트의 정의는 코드({@link AdminNotificationEvent})가 원천이고 이 표는
 * <b>운영자가 바꾸는 값</b>(사용 여부 {@code enabled}, 이메일 전달 {@code emailEnabled}, 브라우저 알림 선택 {@code browserEnabled}, 기본 수신 {@code defaultRecipients})만 든다. Slack · Mattermost 도구와 추가 수신 팀은 연결 표
 * ({@code notification_event_channels}·{@code notification_event_teams})에 있다. 서버가 뜰 때 코드와 맞춘다 — 없는 이벤트는 기본값으로 넣고,
 * 코드에서 사라진 이벤트는 지우며, 운영자가 바꾼 값은 덮어쓰지 않는다.
 */
@Entity
@Table(name = "notification_events")
@DynamicUpdate
public class NotificationEventEntity {

    @Id
    private String code;

    private boolean enabled;
    private boolean emailEnabled;

    /** 브라우저 알림 전달 — 서버가 하는 일은 선택을 저장하는 것뿐이다(브라우저 알림은 순수 클라이언트 동작) */
    private boolean browserEnabled;

    /**
     * 기본 수신 규칙(권한자 전원 등)으로도 보내는가(V60) — false 면 고른 추가 수신 팀의 팀원에게만 보낸다. 본인 전용·필수 이벤트({@code broadcastAllowed=false})는 늘 true 다.
     * 처음 값은 true(지금 동작 그대로).
     */
    private boolean defaultRecipients = true;

    private Instant updatedAt;
    private Long updatedBy;

    protected NotificationEventEntity() {
    }

    /** 코드 정의의 기본값으로 만든다(맞춤 때) */
    public static NotificationEventEntity of(AdminNotificationEvent event, Instant now) {
        NotificationEventEntity entity = new NotificationEventEntity();
        entity.code = event.code();
        entity.enabled = event.defaultEnabled();
        entity.emailEnabled = event.defaultEmailEnabled();
        entity.updatedAt = now;
        return entity;
    }

    /** 운영자가 사용 여부·이메일 전달·브라우저 알림·기본 수신(권한자 전원에게 보내기)을 바꾼다 */
    public void change(boolean enabled, boolean emailEnabled, boolean browserEnabled, boolean defaultRecipients, long operatorId, Instant now) {
        this.enabled = enabled;
        this.emailEnabled = emailEnabled;
        this.browserEnabled = browserEnabled;
        this.defaultRecipients = defaultRecipients;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /** 코드 정의가 바뀌어 지금 값이 정의와 맞지 않을 때(필수 이벤트가 꺼져 있음, 이메일을 보낼 수 없는 이벤트가 켜져 있음) 맞춘다. 바꿨으면 true */
    public boolean reconcile(AdminNotificationEvent event, Instant now) {
        boolean changed = false;
        if (event.mandatory() && !enabled) {
            enabled = true;
            changed = true;
        }
        if (!event.emailable() && emailEnabled) {
            emailEnabled = false;
            changed = true;
        }
        if (!event.broadcastAllowed() && !defaultRecipients) { // 본인 전용·필수 이벤트는 팀에만 보낼 수 없다
            defaultRecipients = true;
            changed = true;
        }
        if (changed) {
            updatedAt = now;
        }
        return changed;
    }

    public String getCode() {
        return code;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isBrowserEnabled() {
        return browserEnabled;
    }

    public boolean isDefaultRecipients() {
        return defaultRecipients;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

package com.community.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 기본 제공 알림 도구(이메일 · 브라우저)의 바꿀 수 있는 상태 — V41 의 {@code notification_builtin_channels}. 도구의 정의는 코드가 내려주고 여기에는 사용 여부와
 * 마지막 연결 시험 결과만 있다. 행이 없으면 켜져 있고 시험한 적이 없는 것이다. 서랍은 항상 켜져 있어 행이 없다.
 */
@Entity
@Table(name = "notification_builtin_channels")
@DynamicUpdate
public class NotificationBuiltinChannelEntity {

    /** 기본 제공 도구의 id — 이벤트의 {@code channels} 에서도 이 값으로 가리킨다 */
    public static final String DRAWER = "drawer";
    public static final String EMAIL = "email";
    public static final String BROWSER = "browser";

    @Id
    private String channelKey;

    private boolean enabled;
    private Instant lastTestAt;
    private Boolean lastTestOk;
    private String lastTestCode;
    private Instant updatedAt;
    private Long updatedBy;

    protected NotificationBuiltinChannelEntity() {
    }

    public static NotificationBuiltinChannelEntity create(String channelKey, Instant now) {
        NotificationBuiltinChannelEntity channel = new NotificationBuiltinChannelEntity();
        channel.channelKey = channelKey;
        channel.enabled = true;
        channel.updatedAt = now;
        return channel;
    }

    public void toggle(boolean enabled, long operatorId, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public void recordTest(boolean ok, String code, Instant now) {
        this.lastTestAt = now;
        this.lastTestOk = ok;
        this.lastTestCode = code;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public Boolean getLastTestOk() {
        return lastTestOk;
    }

    public String getLastTestCode() {
        return lastTestCode;
    }
}

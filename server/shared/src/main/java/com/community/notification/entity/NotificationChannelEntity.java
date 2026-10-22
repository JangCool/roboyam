package com.community.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 알림 도구(Slack · Mattermost) — V41__teams_notification_channels.sql 의 {@code notification_channels}.
 *
 * <p>비밀(봇 토큰·웹훅 주소)은 {@code ROBOYAM_CRYPTO_KEY} 로 암호화한 <b>암호문</b>만 들고({@code *Enc}) 평문은 어디에도 두지 않는다.
 * 암호문도 응답·로그·감사·{@code toString} 에 내보내지 않는다(이 클래스는 {@code toString} 을 재정의하지 않아 기본 {@code Object.toString} 이다).
 * 마지막 연결 시험은 시각·성공 여부·짧은 사유 코드만 남긴다(외부 응답 본문은 저장하지 않는다).
 */
@Entity
@Table(name = "notification_channels")
@DynamicUpdate
public class NotificationChannelEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private NotificationChannelType channelType;

    private String channelName;
    private String description;
    private boolean enabled;

    /** Slack 채널 / Mattermost(봇 방식) 채널 */
    private String channelTarget;

    /** Mattermost 연결 방식: webhook | bot (Slack 은 null) */
    private String mattermostMode;

    /** Mattermost(봇 방식) 서버 주소 */
    private String serverUrl;

    private String botTokenEnc;
    private String webhookUrlEnc;
    private Instant lastTestAt;
    private Boolean lastTestOk;
    private String lastTestCode;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected NotificationChannelEntity() {
    }

    public static NotificationChannelEntity create(long id, NotificationChannelType type, String name, String description, boolean enabled, String channelTarget,
                                                   String mattermostMode, String serverUrl, String botTokenEnc, String webhookUrlEnc, long operatorId, Instant now) {
        NotificationChannelEntity channel = new NotificationChannelEntity();
        channel.id = id;
        channel.channelType = type;
        channel.createdAt = now;
        channel.createdBy = operatorId;
        channel.change(name, description, enabled, channelTarget, mattermostMode, serverUrl, botTokenEnc, webhookUrlEnc, operatorId, now);
        return channel;
    }

    /** 이름·설명·사용 여부·설정을 고친다 — 종류는 못 바꾼다. 안 쓰는 비밀은 {@code null} 로 넘겨 지운다 */
    public void change(String name, String description, boolean enabled, String channelTarget, String mattermostMode, String serverUrl, String botTokenEnc,
                       String webhookUrlEnc, long operatorId, Instant now) {
        this.channelName = name;
        this.description = description;
        this.enabled = enabled;
        this.channelTarget = channelTarget;
        this.mattermostMode = mattermostMode;
        this.serverUrl = serverUrl;
        this.botTokenEnc = botTokenEnc;
        this.webhookUrlEnc = webhookUrlEnc;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public void toggle(boolean enabled, long operatorId, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 연결 시험 결과를 남긴다 — 사유 코드만(외부 응답 본문은 저장하지 않는다) */
    public void recordTest(boolean ok, String code, Instant now) {
        this.lastTestAt = now;
        this.lastTestOk = ok;
        this.lastTestCode = code;
    }

    /** 접속 정보가 바뀌어 지난 시험 결과가 더는 맞지 않을 때 비운다(시험한 적이 없는 상태로) */
    public void clearTest() {
        this.lastTestAt = null;
        this.lastTestOk = null;
        this.lastTestCode = null;
    }

    public Long getId() {
        return id;
    }

    public NotificationChannelType getChannelType() {
        return channelType;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getChannelTarget() {
        return channelTarget;
    }

    public String getMattermostMode() {
        return mattermostMode;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getBotTokenEnc() {
        return botTokenEnc;
    }

    public String getWebhookUrlEnc() {
        return webhookUrlEnc;
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

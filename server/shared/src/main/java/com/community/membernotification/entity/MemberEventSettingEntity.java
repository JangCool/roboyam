package com.community.membernotification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 회원 이벤트 설정 한 줄 — V58__member_event_settings.sql 의 {@code member_event_settings}. 이벤트의 정의는 코드({@link MemberEvent})가 원천이고 이 표는 운영자가 바꾸는 값만 든다.
 * 줄이 없으면 코드의 처음 값이다. 발행기({@code MemberNotificationPublisher})는 JDBC 로 읽는다(세이브포인트 안).
 */
@Entity
@Table(name = "member_event_settings")
@DynamicUpdate
public class MemberEventSettingEntity {

    @Id
    private String code;

    private boolean enabled;
    private boolean inApp;
    private boolean email;
    private boolean memberCanTurnOff;
    private Instant updatedAt;
    private Long updatedBy;

    protected MemberEventSettingEntity() {
    }

    /** 코드의 처음 값으로 만든다 */
    public static MemberEventSettingEntity of(MemberEvent event, Instant now) {
        MemberEventSettingEntity entity = new MemberEventSettingEntity();
        entity.code = event.code();
        entity.enabled = event.defaultEnabled();
        entity.inApp = event.defaultInApp();
        entity.email = event.defaultEmail();
        entity.memberCanTurnOff = event.defaultMemberCanTurnOff();
        entity.updatedAt = now;
        return entity;
    }

    public void change(boolean enabled, boolean inApp, boolean email, boolean memberCanTurnOff, long operatorId, Instant now) {
        this.enabled = enabled;
        this.inApp = inApp;
        this.email = email;
        this.memberCanTurnOff = memberCanTurnOff;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public String getCode() {
        return code;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isInApp() {
        return inApp;
    }

    public boolean isEmail() {
        return email;
    }

    public boolean isMemberCanTurnOff() {
        return memberCanTurnOff;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}

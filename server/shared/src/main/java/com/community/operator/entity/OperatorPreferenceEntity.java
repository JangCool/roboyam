package com.community.operator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 운영자 개인 설정 — V22__operator_preferences.sql 의 {@code operator_preferences}. 운영자마다 최대 한 행이고 처음 저장할 때 만든다(행이 없으면 처음 값으로 본다).
 *
 * <p>내 표시 형식({@code display*})은 「쓴다」({@code displayUse}) 와 값 네 칸이다. 꺼도 값은 남는다. 값은 한 번도 저장하지 않았으면 넷 다 null 이다.
 * 이메일 알림({@code emailNotification})은 처음 값이 켜짐이다.
 */
@Entity
@Table(name = "operator_preferences")
@DynamicUpdate
public class OperatorPreferenceEntity {

    @Id
    private Long operatorId;

    private boolean displayUse;
    private String displayLanguage;
    private String displayZoneId;
    private String displayTimeFormat;
    private String displayDateFormat;
    private boolean emailNotification;
    private Instant updatedAt;

    protected OperatorPreferenceEntity() {
    }

    /** 처음 값(내 표시 형식 안 씀, 값 없음, 이메일 알림 켜짐)으로 만든다 */
    public static OperatorPreferenceEntity create(long operatorId, Instant now) {
        OperatorPreferenceEntity preference = new OperatorPreferenceEntity();
        preference.operatorId = operatorId;
        preference.emailNotification = true;
        preference.updatedAt = now;
        return preference;
    }

    /** 내 표시 형식을 정한다 — 값 네 칸은 검사를 마친 것이다 */
    public void changeDisplay(boolean use, String language, String zoneId, String timeFormat, String dateFormat, Instant now) {
        this.displayUse = use;
        this.displayLanguage = language;
        this.displayZoneId = zoneId;
        this.displayTimeFormat = timeFormat;
        this.displayDateFormat = dateFormat;
        this.updatedAt = now;
    }

    public void changeEmailNotification(boolean enabled, Instant now) {
        this.emailNotification = enabled;
        this.updatedAt = now;
    }

    /** 내가 정한 값이 있는가 — 한 번이라도 저장했으면 true */
    public boolean hasDisplayValues() {
        return displayLanguage != null;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public boolean isDisplayUse() {
        return displayUse;
    }

    public String getDisplayLanguage() {
        return displayLanguage;
    }

    public String getDisplayZoneId() {
        return displayZoneId;
    }

    public String getDisplayTimeFormat() {
        return displayTimeFormat;
    }

    public String getDisplayDateFormat() {
        return displayDateFormat;
    }

    public boolean isEmailNotification() {
        return emailNotification;
    }
}

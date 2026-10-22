package com.community.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 공개 사이트 회원이 고른 표시 형식(언어·시간대·시간 형식·날짜 형식) — V52 의 {@code member_display_preferences}. 회원마다 최대 한 행이다.
 *
 * <p>행이 있으면 그 값이 그 회원의 효과값(개인 값이 네 칸을 통째로 덮는다 — 칸별 상속 없음), 없으면 사이트 표시 기본값을 따른다.
 * 「기본값으로 되돌리기」는 행을 지운다. 기본값과 같은 값을 저장해도 행이 생기고, 그 뒤 기본값이 바뀌어도 따라가지 않는다.
 * 값 검사는 부르는 쪽이 {@code DisplayValuesPolicy} 로 끝낸다.
 */
@Entity
@Table(name = "member_display_preferences")
@DynamicUpdate
public class MemberDisplayPreferenceEntity {

    @Id
    private Long userId;

    private String language;
    private String zoneId;
    private String timeFormat;
    private String dateFormat;
    private Instant createdAt;
    private Instant updatedAt;

    protected MemberDisplayPreferenceEntity() {
    }

    /** 처음 저장할 때 — 네 칸은 검사를 마친 값이다 */
    public static MemberDisplayPreferenceEntity create(long userId, String language, String zoneId, String timeFormat, String dateFormat, Instant now) {
        MemberDisplayPreferenceEntity preference = new MemberDisplayPreferenceEntity();
        preference.userId = userId;
        preference.language = language;
        preference.zoneId = zoneId;
        preference.timeFormat = timeFormat;
        preference.dateFormat = dateFormat;
        preference.createdAt = now;
        preference.updatedAt = now;
        return preference;
    }

    /** 값을 바꾼다 — 네 칸은 검사를 마친 값이다. 같은 값이면 아무것도 바꾸지 않는다 */
    public void change(String language, String zoneId, String timeFormat, String dateFormat, Instant now) {
        if (language.equals(this.language) && zoneId.equals(this.zoneId) && timeFormat.equals(this.timeFormat) && dateFormat.equals(this.dateFormat)) {
            return;
        }
        this.language = language;
        this.zoneId = zoneId;
        this.timeFormat = timeFormat;
        this.dateFormat = dateFormat;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLanguage() {
        return language;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

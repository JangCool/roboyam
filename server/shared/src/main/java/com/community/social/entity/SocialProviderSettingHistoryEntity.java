package com.community.social.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 소셜 로그인 공급자 설정 변경 이력 한 줄 — V53__social_provider_setting_history.sql. 쓰기만 하고 고치지 않는다(보관 무기한).
 * {@code changes} 에는 <b>시크릿 값이 없다</b>(SET·CHANGED·CLEARED 표시만)이고 클라이언트 ID 는 끝 4자리만 보이게 가린 값이다.
 */
@Entity
@Table(name = "social_provider_setting_history")
public class SocialProviderSettingHistoryEntity {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String SOURCE_INSTALL = "INSTALL";
    public static final String SOURCE_ADMIN = "ADMIN";

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private SocialProvider provider;

    private String action;

    private String source;

    /** 바꾼 운영자({@code operators.id}). FK 가 없어 지워진 운영자일 수 있다 */
    private Long operatorId;

    private Instant changedAt;

    /** {@code [{field, before, after}]} JSON 글자 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected SocialProviderSettingHistoryEntity() {
    }

    public static SocialProviderSettingHistoryEntity create(long id, SocialProvider provider, String action, String source, Long operatorId, Instant changedAt,
                                                            String changesJson) {
        SocialProviderSettingHistoryEntity row = new SocialProviderSettingHistoryEntity();
        row.id = id;
        row.provider = provider;
        row.action = action;
        row.source = source;
        row.operatorId = operatorId;
        row.changedAt = changedAt;
        row.changes = changesJson;
        return row;
    }

    public Long getId() {
        return id;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getAction() {
        return action;
    }

    public String getSource() {
        return source;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getChanges() {
        return changes;
    }
}

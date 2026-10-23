package com.community.notificationtemplate.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 문구 · 메일 템플릿 · Slack/Mattermost 템플릿 변경 이력 한 줄의 공통 칸 — 설정 이력({@code mail_setting_history})과 같은 모양이다. 고치거나 지우지 않는다.
 * 바뀐 칸은 JSON 배열 글자 {@code [{field, before, after}]} 다.
 */
@MappedSuperclass
public abstract class TemplateHistoryBase {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String SOURCE_ADMIN = "ADMIN";

    @Id
    private Long id;

    private String action;
    private String source;
    /** 바꾼 운영자 — 운영자가 지워져도 값은 남는다(FK 없음) */
    private Long operatorId;
    private Instant changedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected TemplateHistoryBase() {
    }

    protected void fill(long id, String action, long operatorId, Instant changedAt, String changesJson) {
        this.id = id;
        this.action = action;
        this.source = SOURCE_ADMIN;
        this.operatorId = operatorId;
        this.changedAt = changedAt;
        this.changes = changesJson;
    }

    public Long getId() {
        return id;
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

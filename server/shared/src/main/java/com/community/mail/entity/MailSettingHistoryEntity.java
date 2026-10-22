package com.community.mail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 메일 서버 설정 변경 이력 한 줄 — V54__mail_setting_history.sql. 값이 실제로 바뀐 저장마다 하나이고 고치거나 지우지 않는다.
 * 바뀐 칸은 JSON 배열 글자({@code changes})이고 <b>비밀번호는 값 없이</b> {@code SET}·{@code CHANGED}·{@code CLEARED} 만 담는다.
 */
@Entity
@Table(name = "mail_setting_history")
public class MailSettingHistoryEntity {

    @Id
    private Long id;

    private Instant changedAt;

    /** 바꾼 운영자. 운영자가 지워지면 null */
    private Long operatorId;

    @Enumerated(EnumType.STRING)
    private MailSettingHistorySource source;

    /** 바뀐 칸 목록(JSON 배열 글자) — {@code MailSettingChange} 의 모양 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected MailSettingHistoryEntity() {
    }

    public static MailSettingHistoryEntity create(long id, Instant changedAt, long operatorId, MailSettingHistorySource source, String changesJson) {
        MailSettingHistoryEntity row = new MailSettingHistoryEntity();
        row.id = id;
        row.changedAt = changedAt;
        row.operatorId = operatorId;
        row.source = source;
        row.changes = changesJson;
        return row;
    }

    public Long getId() {
        return id;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public MailSettingHistorySource getSource() {
        return source;
    }

    public String getChanges() {
        return changes;
    }
}

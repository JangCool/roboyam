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
 * 바뀐 칸은 JSON 배열 글자({@code changes}, {@code MailSettingChange} 모양)이고 <b>비밀번호는 값 없이</b> {@code SET}·{@code CHANGED}·{@code CLEARED} 만 담는다.
 */
@Entity
@Table(name = "mail_setting_history")
public class MailSettingHistoryEntity {

    /** 행을 처음 만들었다(설치 마법사, 또는 설치 때 건너뛰고 관리자 화면에서 처음 저장) */
    public static final String ACTION_CREATE = "CREATE";
    /** 있던 행을 고쳤다 */
    public static final String ACTION_UPDATE = "UPDATE";

    @Id
    private Long id;

    /** {@link #ACTION_CREATE} · {@link #ACTION_UPDATE} (표의 CHECK 는 소셜 이력과 같게 {@code DELETE} 도 받는다 — 메일 설정 지우기는 아직 없다) */
    private String action;

    @Enumerated(EnumType.STRING)
    private MailSettingHistorySource source;

    /** 바꾼 운영자. 운영자가 지워져도 값은 남는다(FK 없음 — 감사 기록과 같다) */
    private Long operatorId;

    private Instant changedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected MailSettingHistoryEntity() {
    }

    public static MailSettingHistoryEntity create(long id, String action, MailSettingHistorySource source, long operatorId, Instant changedAt, String changesJson) {
        MailSettingHistoryEntity row = new MailSettingHistoryEntity();
        row.id = id;
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

    public String getAction() {
        return action;
    }

    public MailSettingHistorySource getSource() {
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

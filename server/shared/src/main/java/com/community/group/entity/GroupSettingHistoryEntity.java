package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 소모임 설정 변경 이력 한 줄 — V90 {@code group_setting_history}. 값이 실제로 바뀐 저장마다 하나이고 고치거나 지우지 않는다.
 * {@code changes} 는 {@code [{field, before, after}]} JSON 글자(값은 코드 글자 — 조회가 요청 언어의 표시 글자로 바꾼다).
 */
@Entity
@Table(name = "group_setting_history")
public class GroupSettingHistoryEntity {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private GroupSettingSection section;
    private String action;
    private Long operatorId;
    private Instant changedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected GroupSettingHistoryEntity() {
    }

    public static GroupSettingHistoryEntity create(long id, GroupSettingSection section, String action, long operatorId, Instant changedAt, String changesJson) {
        GroupSettingHistoryEntity row = new GroupSettingHistoryEntity();
        row.id = id;
        row.section = section;
        row.action = action;
        row.operatorId = operatorId;
        row.changedAt = changedAt;
        row.changes = changesJson;
        return row;
    }

    public Long getId() {
        return id;
    }

    public GroupSettingSection getSection() {
        return section;
    }

    public String getAction() {
        return action;
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

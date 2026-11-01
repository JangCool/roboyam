package com.community.ad.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 광고 노출 자리 설정 변경 이력 한 줄 — V120__ad_placement_history.sql(메일 서버 설정 이력 {@code MailSettingHistoryEntity} 와 같은 짜임). 값이 실제로 바뀐 저장마다 하나이고
 * 고치거나 지우지 않는다. 바뀐 칸은 JSON 배열 글자({@code changes}, {@code [{field, before, after}]})다.
 */
@Entity
@Table(name = "ad_placement_history")
public class AdPlacementHistoryEntity {

    /** 행을 처음 만들었다(그 전까지는 서버 기본값) */
    public static final String ACTION_CREATE = "CREATE";
    /** 있던 행을 고쳤다 */
    public static final String ACTION_UPDATE = "UPDATE";
    /** 관리자 화면({@code /ads/placements})의 저장 — 지금은 이것뿐이다 */
    public static final String SOURCE_ADMIN = "ADMIN";

    @Id
    private Long id;

    /** {@link #ACTION_CREATE} · {@link #ACTION_UPDATE} */
    private String action;

    /** {@link #SOURCE_ADMIN} */
    private String source;

    /** 바꾼 운영자. 운영자가 지워져도 값은 남는다(FK 없음 — 감사 기록과 같다) */
    private Long operatorId;

    private Instant changedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected AdPlacementHistoryEntity() {
    }

    public static AdPlacementHistoryEntity create(long id, String action, String source, long operatorId, Instant changedAt, String changesJson) {
        AdPlacementHistoryEntity row = new AdPlacementHistoryEntity();
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

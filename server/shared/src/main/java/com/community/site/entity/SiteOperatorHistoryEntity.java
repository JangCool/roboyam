package com.community.site.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 운영 주체 단계 전환 이력 한 줄 — V159 의 {@code site_operator_history}. 고치지 않는다 */
@Entity
@Table(name = "site_operator_history")
public class SiteOperatorHistoryEntity {

    @Id
    private Long id;

    private Instant changedAt;

    @Enumerated(EnumType.STRING)
    private SiteOperatorStage fromStage;

    @Enumerated(EnumType.STRING)
    private SiteOperatorStage toStage;

    /** 바꾼 운영자({@code operators.id}). FK 없음 — 운영자가 지워져도 남는다. 마이그레이션·시드가 남긴 줄은 null */
    private Long changedBy;

    protected SiteOperatorHistoryEntity() {
    }

    public static SiteOperatorHistoryEntity create(long id, SiteOperatorStage from, SiteOperatorStage to, Long operatorId, Instant now) {
        SiteOperatorHistoryEntity row = new SiteOperatorHistoryEntity();
        row.id = id;
        row.changedAt = now;
        row.fromStage = from;
        row.toStage = to;
        row.changedBy = operatorId;
        return row;
    }

    public Long getId() {
        return id;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public SiteOperatorStage getFromStage() {
        return fromStage;
    }

    public SiteOperatorStage getToStage() {
        return toStage;
    }

    public Long getChangedBy() {
        return changedBy;
    }
}

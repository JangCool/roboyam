package com.community.analytics.entity;

import com.community.analytics.settings.StatsCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 통계 수집 설정 행 — shared V133·V135 {@code stats_collection_settings}(id = 1, 마이그레이션이 기본값으로 만들어 둔다). 메인 DB 다(크론이 이 행을 읽는다).
 * 저장은 잠금 조회({@code StatsCollectionStore#lock}) 뒤 {@link #apply} 로 바꾼다(변경 감지).
 */
@Entity
@Table(name = "stats_collection_settings")
public class StatsCollectionSettingEntity {

    public static final short ROW_ID = 1;

    @Id
    private Short id;

    private boolean enabled;
    private boolean visits;
    private boolean rawEvents;
    private int rawRetentionDays;
    private boolean referrers;
    private boolean flows;
    private boolean environment;
    private boolean searchTerms;
    private boolean bots;
    private boolean notificationSends;
    private boolean boardActivity;
    /** 마지막 저장(저장한 적 없으면 null) */
    private Instant updatedAt;
    /** 마지막으로 저장한 운영자(FK 없음 — 지워져도 남는다) */
    private Long updatedBy;

    protected StatsCollectionSettingEntity() {
    }

    /** 행이 없을 때(마이그레이션이 만든 행을 누가 지웠을 때) 새로 만든다 */
    public static StatsCollectionSettingEntity create(StatsCollection value, long operatorId, Instant now) {
        StatsCollectionSettingEntity row = new StatsCollectionSettingEntity();
        row.id = ROW_ID;
        row.apply(value, operatorId, now);
        return row;
    }

    public void apply(StatsCollection v, long operatorId, Instant now) {
        this.enabled = v.enabled();
        this.visits = v.visits();
        this.rawEvents = v.rawEvents();
        this.rawRetentionDays = v.rawRetentionDays();
        this.referrers = v.referrers();
        this.flows = v.flows();
        this.environment = v.environment();
        this.searchTerms = v.searchTerms();
        this.bots = v.bots();
        this.notificationSends = v.notificationSends();
        this.boardActivity = v.boardActivity();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 저장한 값(보존 일수가 허용 값이 아니면 기본 일수로 읽는다) */
    public StatsCollection value() {
        return new StatsCollection(enabled, visits, rawEvents, StatsCollection.RETENTION_DAYS.contains(rawRetentionDays) ? rawRetentionDays
                : StatsCollection.DEFAULT_RETENTION_DAYS, referrers, flows, environment, searchTerms, bots, notificationSends, boardActivity);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}

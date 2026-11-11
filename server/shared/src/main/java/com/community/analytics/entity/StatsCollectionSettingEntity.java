package com.community.analytics.entity;

import com.community.analytics.settings.StatsCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 통계 수집 설정 행 — shared V133·V135·V189 {@code stats_collection_settings}(id = 1, 마이그레이션이 기본값으로 만들어 둔다). 메인 DB 다(크론이 이 행을 읽는다).
 * 저장은 잠금 조회({@code StatsCollectionStore#lock}) 뒤 {@link #apply} 로 바꾼다(변경 감지).
 *
 * <p>원본 보관은 {@code raw_retention_months}(V189, smallint 2·3·6·12) 만 매핑한다. 옛 칸 {@code raw_retention_days} 는 매핑하지 않는다 — 롤링 배포 동안 옛 서버만 읽고 쓰며
 * (행을 새로 만들 때는 DB 기본값이 들어간다) 다음 버전에서 지운다.
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
    private short rawRetentionMonths;
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
        this.rawRetentionMonths = (short) v.rawRetentionMonths();
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

    /** 저장한 값(보관 개월 수가 허용 값이 아니면 기본 개월 수로 읽는다) */
    public StatsCollection value() {
        int months = rawRetentionMonths;
        return new StatsCollection(enabled, visits, rawEvents, StatsCollection.RETENTION_MONTHS.contains(months) ? months : StatsCollection.DEFAULT_RETENTION_MONTHS,
                referrers, flows, environment, searchTerms, bots, notificationSends, boardActivity);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}

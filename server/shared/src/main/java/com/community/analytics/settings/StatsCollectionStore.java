package com.community.analytics.settings;

import com.community.analytics.entity.QStatsCollectionSettingEntity;
import com.community.analytics.entity.QStatsCollectionSettingHistoryEntity;
import com.community.analytics.entity.StatsCollectionSettingEntity;
import com.community.analytics.entity.StatsCollectionSettingHistoryEntity;
import com.community.analytics.repository.master.StatsCollectionSettingHistoryRepository;
import com.community.analytics.repository.master.StatsCollectionSettingRepository;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 통계 수집 설정 행(shared V133 {@code stats_collection_settings}, id = 1 — 마이그레이션이 기본값으로 만들어 둔다)과 변경 이력({@code stats_collection_setting_history}).
 * 메인 DB 다(분석 DB 가 아니다 — 크론이 이 행을 읽는다). <b>부르는 쪽 트랜잭션에 참여한다.</b> 저장은 메일 설정과 같은 방식 — 행을 잠가 읽고({@link #lock})
 * 엔티티를 고친다(변경 감지, 다음 질의나 커밋 때 나간다).
 */
@Repository
public class StatsCollectionStore extends RoboyamRepositoryQDSL {

    /** 저장한 값 + 마지막 저장(저장한 적 없으면 시각·운영자 null, 운영자가 지워졌으면 이름 null) */
    public record Stored(StatsCollection value, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    /** 이력 한 줄 + 바꾼 운영자의 지금 이름(지워졌으면 null) */
    public record HistoryRow(long id, String action, String source, Long operatorId, String operatorName, Instant changedAt, String changesJson) {
    }

    private static final QStatsCollectionSettingEntity S = QStatsCollectionSettingEntity.statsCollectionSettingEntity;
    private static final QStatsCollectionSettingHistoryEntity H = QStatsCollectionSettingHistoryEntity.statsCollectionSettingHistoryEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    private final StatsCollectionSettingRepository settings;
    private final StatsCollectionSettingHistoryRepository history;

    public StatsCollectionStore(JPAQueryFactory query, StatsCollectionSettingRepository settings, StatsCollectionSettingHistoryRepository history) {
        super(query);
        this.settings = settings;
        this.history = history;
    }

    public Optional<Stored> find() {
        Tuple t = query.select(S, OPERATOR.operatorName).from(S).leftJoin(OPERATOR).on(OPERATOR.id.eq(S.updatedBy)).where(S.id.eq(StatsCollectionSettingEntity.ROW_ID))
                .fetchOne();
        if (t == null) {
            return Optional.empty();
        }
        StatsCollectionSettingEntity row = t.get(S);
        return Optional.of(new Stored(row.value(), row.getUpdatedAt(), row.getUpdatedBy(), t.get(OPERATOR.operatorName)));
    }

    /**
     * 저장 트랜잭션 안에서 행을 잠그고 읽는다(동시 저장이 줄을 선다) — {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE}, 이 표를 FK 로 가리키는 표가 없다).
     * 저장의 첫 읽기라 1차 캐시에 낡은 값이 없다.
     */
    public Optional<Stored> lock() {
        StatsCollectionSettingEntity row = query.selectFrom(S).where(S.id.eq(StatsCollectionSettingEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
        return Optional.ofNullable(row).map(r -> new Stored(r.value(), r.getUpdatedAt(), r.getUpdatedBy(), null));
    }

    /** 값을 저장한다 — 행을 고치고(변경 감지), 행이 없으면 만든다 */
    public void save(StatsCollection v, long operatorId, Instant now) {
        settings.findById(StatsCollectionSettingEntity.ROW_ID).ifPresentOrElse(row -> row.apply(v, operatorId, now),
                () -> settings.persist(StatsCollectionSettingEntity.create(v, operatorId, now)));
    }

    public void addHistory(long id, long operatorId, Instant changedAt, String changesJson) {
        history.persist(StatsCollectionSettingHistoryEntity.update(id, operatorId, changedAt, changesJson));
    }

    /** 최신순(시각, 같으면 id 큰 쪽) — ix_stats_collection_history_changed */
    public List<HistoryRow> history(int page, int size) {
        return query.select(Projections.constructor(HistoryRow.class, H.id, H.action, H.source, H.operatorId, OPERATOR.operatorName, H.changedAt, H.changes)).from(H)
                .leftJoin(OPERATOR).on(OPERATOR.id.eq(H.operatorId)).orderBy(H.changedAt.desc(), H.id.desc()).offset((long) page * size).limit(size).fetch();
    }

    public long historyCount() {
        Long n = query.select(H.count()).from(H).fetchOne();
        return n == null ? 0 : n;
    }
}

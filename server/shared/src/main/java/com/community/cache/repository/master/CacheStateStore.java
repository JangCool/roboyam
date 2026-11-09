package com.community.cache.repository.master;

import com.community.cache.entity.QCacheGlobalSettingEntity;
import com.community.cache.entity.QCacheNodeStateEntity;
import com.community.cache.entity.QCacheRefreshLogEntity;
import com.community.cache.entity.QCacheSettingChangeEntity;
import com.community.cache.entity.QCacheSettingEntity;
import com.community.cache.entity.CacheGlobalSettingEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 캐시 설정·서버 보고·기록 읽기와 서버 보고 쓰기(shared V175). 읽기는 프로젝션(영속성 컨텍스트에 올리지 않는다 — 주기 읽기는 트랜잭션 없이 질의 하나씩).
 *
 * <p>Native SQL 로 남긴 것: 서버 보고 덮어쓰기({@link #report} — {@code INSERT … ON CONFLICT DO UPDATE} 를 캐시 수만큼 한 번에 {@code batchUpdate}) —
 * 처음 보고할 때는 행이 없고, 보고마다 행을 읽고 고치면 질의가 캐시 수 × 2 가 된다.
 */
@Repository
public class CacheStateStore extends RoboyamRepositoryQDSL {

    private static final QCacheSettingEntity SETTING = QCacheSettingEntity.cacheSettingEntity;
    private static final QCacheGlobalSettingEntity GLOBAL = QCacheGlobalSettingEntity.cacheGlobalSettingEntity;
    private static final QCacheNodeStateEntity NODE = QCacheNodeStateEntity.cacheNodeStateEntity;
    private static final QCacheRefreshLogEntity LOG = QCacheRefreshLogEntity.cacheRefreshLogEntity;
    private static final QCacheSettingChangeEntity CHANGE = QCacheSettingChangeEntity.cacheSettingChangeEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    /** 캐시별 설정 한 줄 — {@code ttlSeconds} null = 정의의 기본 TTL */
    public record SettingRow(String cacheKey, Integer ttlSeconds, boolean enabled, long generation, Instant changedAt) {
    }

    /** 전체 스위치 */
    public record GlobalRow(boolean enabled, Instant changedAt) {
    }

    /** 서버 · 캐시 하나의 보고 */
    public record NodeStateRow(String nodeId, String cacheKey, String role, long appliedGeneration, boolean appliedEnabled, Instant lastClearedAt,
                               Instant lastCheckedAt, long entryCount, long hitCount, long missCount, Instant reportedAt) {
    }

    /** 갱신 기록 한 줄(운영자 이름 — 지워진 운영자·없음은 null) */
    public record RefreshLogRow(long id, String cacheKey, String triggerKind, boolean allCaches, String sourceCode, String sourceDetail, String operatorName,
                                long generation, Instant requestedAt) {
    }

    /** 설정 변경 한 줄 */
    public record SettingChangeRow(long id, String cacheKey, String kind, Integer fromSeconds, Integer toSeconds, Boolean enabled, String operatorName,
                                   Instant changedAt) {
    }

    private final JdbcTemplate jdbc;

    public CacheStateStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 모든 캐시 설정(행이 있는 것) */
    public List<SettingRow> settings() {
        return query.select(Projections.constructor(SettingRow.class, SETTING.cacheKey, SETTING.ttlSeconds, SETTING.enabled, SETTING.generation, SETTING.changedAt))
                .from(SETTING).fetch();
    }

    /** 전체 스위치 — 행이 없으면 비어 있다(켜짐으로 본다) */
    public Optional<GlobalRow> global() {
        return Optional.ofNullable(query.select(Projections.constructor(GlobalRow.class, GLOBAL.enabled, GLOBAL.changedAt)).from(GLOBAL)
                .where(GLOBAL.id.eq(CacheGlobalSettingEntity.ROW_ID)).fetchOne());
    }

    /** 이 시각 뒤에 보고한 서버들의 상태(서버 · 캐시 차례) */
    public List<NodeStateRow> nodeStates(Instant reportedSince) {
        return query.select(Projections.constructor(NodeStateRow.class, NODE.nodeId, NODE.cacheKey, NODE.role, NODE.appliedGeneration, NODE.appliedEnabled,
                        NODE.lastClearedAt, NODE.lastCheckedAt, NODE.entryCount, NODE.hitCount, NODE.missCount, NODE.reportedAt))
                .from(NODE).where(NODE.reportedAt.goe(reportedSince)).orderBy(NODE.nodeId.asc(), NODE.cacheKey.asc()).fetch();
    }

    /** 오래 보고가 없는 줄을 지운다(서버를 내렸거나 이름이 바뀐 경우) — 지운 수 */
    public long deleteStale(Instant reportedBefore) {
        return query.delete(NODE).where(NODE.reportedAt.lt(reportedBefore)).execute();
    }

    /**
     * 이 서버의 상태를 한 번에 덮어쓴다.
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT (node_id, cache_key) DO UPDATE} 를 {@code batchUpdate} 로 — 처음 보고하는 서버는 행이 없고, 서버마다 10초에 한 번
     * 캐시 수만큼의 줄을 질의 한 묶음으로 쓴다.
     */
    public void report(List<NodeStateRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("INSERT INTO cache_node_states (node_id, cache_key, role, applied_generation, applied_enabled, last_cleared_at, last_checked_at, "
                        + "entry_count, hit_count, miss_count, reported_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (node_id, cache_key) DO UPDATE SET role = EXCLUDED.role, applied_generation = EXCLUDED.applied_generation, "
                        + "applied_enabled = EXCLUDED.applied_enabled, last_cleared_at = EXCLUDED.last_cleared_at, last_checked_at = EXCLUDED.last_checked_at, "
                        + "entry_count = EXCLUDED.entry_count, hit_count = EXCLUDED.hit_count, miss_count = EXCLUDED.miss_count, reported_at = EXCLUDED.reported_at",
                rows, rows.size(), (ps, r) -> {
                    ps.setString(1, r.nodeId());
                    ps.setString(2, r.cacheKey());
                    ps.setString(3, r.role());
                    ps.setLong(4, r.appliedGeneration());
                    ps.setBoolean(5, r.appliedEnabled());
                    ps.setTimestamp(6, timestamp(r.lastClearedAt()));
                    ps.setTimestamp(7, timestamp(r.lastCheckedAt()));
                    ps.setLong(8, r.entryCount());
                    ps.setLong(9, r.hitCount());
                    ps.setLong(10, r.missCount());
                    ps.setTimestamp(11, timestamp(r.reportedAt()));
                });
    }

    /** 이 캐시의 최근 갱신 기록(최근순) */
    public List<RefreshLogRow> refreshLogs(String cacheKey, int limit) {
        return query.select(Projections.constructor(RefreshLogRow.class, LOG.id, LOG.cacheKey, LOG.triggerKind, LOG.allCaches, LOG.sourceCode, LOG.sourceDetail,
                        OPERATOR.operatorName, LOG.generation, LOG.requestedAt))
                .from(LOG).leftJoin(OPERATOR).on(OPERATOR.id.eq(LOG.operatorId))
                .where(LOG.cacheKey.eq(cacheKey)).orderBy(LOG.requestedAt.desc(), LOG.id.desc()).limit(limit).fetch();
    }

    /** 이 캐시의 설정 변경(TTL · 켜기/끄기) + 전체 켜기/끄기(최근순) */
    public List<SettingChangeRow> settingChanges(String cacheKey, int limit) {
        return query.select(Projections.constructor(SettingChangeRow.class, CHANGE.id, CHANGE.cacheKey, CHANGE.kind, CHANGE.fromSeconds, CHANGE.toSeconds,
                        CHANGE.enabled, OPERATOR.operatorName, CHANGE.changedAt))
                .from(CHANGE).leftJoin(OPERATOR).on(OPERATOR.id.eq(CHANGE.operatorId))
                .where(CHANGE.cacheKey.eq(cacheKey).or(CHANGE.cacheKey.isNull())).orderBy(CHANGE.changedAt.desc(), CHANGE.id.desc()).limit(limit).fetch();
    }

    private static Timestamp timestamp(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }
}

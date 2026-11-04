package com.community.analytics.settings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 통계 수집 설정 행(shared V133 {@code stats_collection_settings}, id = 1 — 마이그레이션이 기본값으로 만들어 둔다)과 변경 이력({@code stats_collection_setting_history}).
 * 메인 DB 다(분석 DB 가 아니다 — 크론이 이 행을 읽는다). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 */
@Repository
public class StatsCollectionStore {

    /** 저장한 값 + 마지막 저장(저장한 적 없으면 시각·운영자 null, 운영자가 지워졌으면 이름 null) */
    public record Stored(StatsCollection value, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    /** 이력 한 줄 + 바꾼 운영자의 지금 이름(지워졌으면 null) */
    public record HistoryRow(long id, String action, String source, Long operatorId, String operatorName, Instant changedAt, String changesJson) {
    }

    private static final String COLUMNS = "s.enabled, s.visits, s.raw_events, s.raw_retention_days, s.referrers, s.flows, s.environment, s.search_terms, s.bots, "
            + "s.notification_sends, s.board_activity, s.updated_at, s.updated_by";

    private final JdbcTemplate jdbc;

    public StatsCollectionStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<Stored> find() {
        return jdbc.query("SELECT " + COLUMNS + ", o.operator_name FROM stats_collection_settings s LEFT JOIN operators o ON o.id = s.updated_by WHERE s.id = 1",
                (rs, i) -> stored(rs, rs.getString("operator_name"))).stream().findFirst();
    }

    /** 저장 트랜잭션 안에서 행을 잠그고 읽는다 */
    public Optional<Stored> lock() {
        return jdbc.query("SELECT " + COLUMNS + " FROM stats_collection_settings s WHERE s.id = 1 FOR UPDATE", (rs, i) -> stored(rs, null)).stream().findFirst();
    }

    public void save(StatsCollection v, long operatorId, Instant now) {
        jdbc.update("INSERT INTO stats_collection_settings (id, enabled, visits, raw_events, raw_retention_days, referrers, flows, environment, search_terms, bots, "
                        + "notification_sends, board_activity, updated_at, updated_by) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
                        + "enabled = EXCLUDED.enabled, visits = EXCLUDED.visits, raw_events = EXCLUDED.raw_events, raw_retention_days = EXCLUDED.raw_retention_days, "
                        + "referrers = EXCLUDED.referrers, flows = EXCLUDED.flows, environment = EXCLUDED.environment, search_terms = EXCLUDED.search_terms, "
                        + "bots = EXCLUDED.bots, notification_sends = EXCLUDED.notification_sends, board_activity = EXCLUDED.board_activity, updated_at = EXCLUDED.updated_at, "
                        + "updated_by = EXCLUDED.updated_by",
                v.enabled(), v.visits(), v.rawEvents(), v.rawRetentionDays(), v.referrers(), v.flows(), v.environment(), v.searchTerms(), v.bots(), v.notificationSends(),
                v.boardActivity(), Timestamp.from(now), operatorId);
    }

    public void addHistory(long id, long operatorId, Instant changedAt, String changesJson) {
        jdbc.update("INSERT INTO stats_collection_setting_history (id, action, source, operator_id, changed_at, changes) VALUES (?, 'UPDATE', 'ADMIN', ?, ?, ?::jsonb)",
                id, operatorId, Timestamp.from(changedAt), changesJson);
    }

    /** 최신순(시각, 같으면 id 큰 쪽) — ix_stats_collection_history_changed */
    public List<HistoryRow> history(int page, int size) {
        return jdbc.query("SELECT h.id, h.action, h.source, h.operator_id, o.operator_name, h.changed_at, h.changes::text FROM stats_collection_setting_history h "
                        + "LEFT JOIN operators o ON o.id = h.operator_id ORDER BY h.changed_at DESC, h.id DESC OFFSET ? LIMIT ?",
                (rs, i) -> new HistoryRow(rs.getLong(1), rs.getString(2), rs.getString(3), (Long) rs.getObject(4, Long.class), rs.getString(5),
                        rs.getTimestamp(6).toInstant(), rs.getString(7)),
                (long) page * size, size);
    }

    public long historyCount() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM stats_collection_setting_history", Long.class);
        return n == null ? 0 : n;
    }

    private static Stored stored(ResultSet rs, String operatorName) throws SQLException {
        int days = rs.getInt("raw_retention_days");
        StatsCollection v = new StatsCollection(rs.getBoolean("enabled"), rs.getBoolean("visits"), rs.getBoolean("raw_events"),
                StatsCollection.RETENTION_DAYS.contains(days) ? days : StatsCollection.DEFAULT_RETENTION_DAYS, rs.getBoolean("referrers"), rs.getBoolean("flows"),
                rs.getBoolean("environment"), rs.getBoolean("search_terms"), rs.getBoolean("bots"), rs.getBoolean("notification_sends"), rs.getBoolean("board_activity"));
        Timestamp at = rs.getTimestamp("updated_at");
        return new Stored(v, at == null ? null : at.toInstant(), rs.getObject("updated_by", Long.class), operatorName);
    }
}

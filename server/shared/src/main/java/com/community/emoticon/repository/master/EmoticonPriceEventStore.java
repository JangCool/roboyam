package com.community.emoticon.repository.master;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 가격 전환 기록({@code emoticon_price_events}, V143 — 추가만). 운영자 가격 바꾸기(사유) · 새 판 승인으로 바뀐 가격(심사 건, 사유 없음).
 * JDBC 인 이유는 {@link EmoticonPackStore} 와 같다.
 */
@Repository
public class EmoticonPriceEventStore {

    /** 가격 전환 한 줄 — 처리한 운영자 이름(지워졌으면 null) */
    public record PriceEventRow(long id, long packId, int fromPrice, int toPrice, String reason, Long reviewId, Instant changedAt, Long changedBy, String changedByName) {
    }

    private static final String SELECT = "SELECT e.id, e.pack_id, e.from_price, e.to_price, e.reason, e.review_id, e.changed_at, e.changed_by, o.operator_name "
            + "FROM emoticon_price_events e LEFT JOIN operators o ON o.id = e.changed_by ";

    private final JdbcTemplate jdbc;

    public EmoticonPriceEventStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(long id, long packId, int fromPrice, int toPrice, String reason, Long reviewId, Instant at, long operatorId) {
        jdbc.update("INSERT INTO emoticon_price_events (id, pack_id, from_price, to_price, reason, review_id, changed_at, changed_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, packId,
                fromPrice, toPrice, reason, reviewId, OffsetDateTime.ofInstant(at, ZoneOffset.UTC), operatorId);
    }

    /** 그 팩의 기록 — 최근이 앞 */
    public List<PriceEventRow> byPack(long packId) {
        return jdbc.query(SELECT + "WHERE e.pack_id = ? ORDER BY e.changed_at DESC, e.id DESC", EmoticonPriceEventStore::rowOf, packId);
    }

    /** 팩마다 가장 최근 기록 */
    public Map<Long, PriceEventRow> latest(Collection<Long> packIds) {
        Map<Long, PriceEventRow> out = new HashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        String marks = String.join(", ", Collections.nCopies(packIds.size(), "?"));
        for (PriceEventRow row : jdbc.query("SELECT * FROM (SELECT DISTINCT ON (e.pack_id) e.id, e.pack_id, e.from_price, e.to_price, e.reason, e.review_id, e.changed_at, "
                + "e.changed_by, o.operator_name FROM emoticon_price_events e LEFT JOIN operators o ON o.id = e.changed_by WHERE e.pack_id IN (" + marks + ") "
                + "ORDER BY e.pack_id, e.changed_at DESC, e.id DESC) x", EmoticonPriceEventStore::rowOf, packIds.toArray())) {
            out.put(row.packId(), row);
        }
        return out;
    }

    private static PriceEventRow rowOf(ResultSet rs, int i) throws SQLException {
        return new PriceEventRow(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getString(5), rs.getObject(6, Long.class),
                rs.getObject(7, OffsetDateTime.class).toInstant(), rs.getObject(8, Long.class), rs.getString(9));
    }
}

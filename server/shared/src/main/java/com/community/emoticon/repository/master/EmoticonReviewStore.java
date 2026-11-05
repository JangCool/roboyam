package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonReviewKind;
import com.community.emoticon.entity.EmoticonReviewStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 로보티콘 심사 건({@code emoticon_reviews}, V140) 읽기·쓰기. Native SQL 인 이유는 {@link EmoticonPackStore} 와 같다 */
@Repository
public class EmoticonReviewStore {

    /** 심사 건 한 줄(처리한 운영자 이름 포함 — 없으면 null) */
    public record ReviewRow(long id, long packId, long revisionId, Long previousRevisionId, long creatorId, EmoticonReviewKind kind, EmoticonReviewStatus status,
                            Instant submittedAt, Instant agreeOwnershipAt, Instant agreeLicenseAt, Instant agreePolicyAt, String consentVersion, Instant decidedAt,
                            Long decidedBy, String decidedByName, String reason, Integer approvedPrice) {
    }

    private static final String COLUMNS = "r.id, r.pack_id, r.revision_id, r.previous_revision_id, r.creator_id, r.kind, r.status, r.submitted_at, r.agree_ownership_at, "
            + "r.agree_license_at, r.agree_policy_at, r.consent_version, r.decided_at, r.decided_by, o.operator_name, r.reason, r.approved_price";
    private static final String FROM = " FROM emoticon_reviews r LEFT JOIN operators o ON o.id = r.decided_by";

    private final JdbcTemplate jdbc;

    public EmoticonReviewStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(long id, long packId, long revisionId, Long previousRevisionId, long creatorId, EmoticonReviewKind kind, Instant now, String consentVersion) {
        Timestamp at = Timestamp.from(now);
        jdbc.update("INSERT INTO emoticon_reviews (id, pack_id, revision_id, previous_revision_id, creator_id, kind, status, submitted_at, agree_ownership_at, "
                        + "agree_license_at, agree_policy_at, consent_version) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)", id, packId, revisionId, previousRevisionId,
                creatorId, kind.name(), at, at, at, at, consentVersion);
    }

    public Optional<ReviewRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE r.id = ?", EmoticonReviewStore::rowOf, id).stream().findFirst();
    }

    /** 처리하기 전에 — 심사 건 행을 잠근다(같은 건을 둘이 동시에 처리하지 못하게) */
    public Optional<ReviewRow> lock(long id) {
        if (jdbc.queryForList("SELECT id FROM emoticon_reviews WHERE id = ? FOR UPDATE", Long.class, id).isEmpty()) {
            return Optional.empty();
        }
        return find(id);
    }

    /** 처리 — 대기 건만(바뀐 줄 수) */
    public int decide(long id, EmoticonReviewStatus status, long operatorId, String reason, Integer approvedPrice, Instant now) {
        return jdbc.update("UPDATE emoticon_reviews SET status = ?, decided_at = ?, decided_by = ?, reason = ?, approved_price = ? WHERE id = ? AND status = 'PENDING'",
                status.name(), Timestamp.from(now), operatorId, reason, approvedPrice, id);
    }

    /** 그 팩의 대기 건 */
    public Optional<ReviewRow> pendingOf(long packId) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE r.pack_id = ? AND r.status = 'PENDING'", EmoticonReviewStore::rowOf, packId).stream().findFirst();
    }

    /** 팩마다 마지막 심사 건 */
    public Map<Long, ReviewRow> latestOf(Collection<Long> packIds) {
        Map<Long, ReviewRow> out = new LinkedHashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT DISTINCT ON (r.pack_id) " + COLUMNS + FROM + " WHERE r.pack_id IN (" + EmoticonPackStore.marks(packIds.size())
                + ") ORDER BY r.pack_id, r.submitted_at DESC, r.id DESC", rs -> {
                    ReviewRow row = rowOf(rs, 0);
                    out.put(row.packId(), row);
                }, packIds.toArray());
        return out;
    }

    /** 작가의 대기 건 수(처음 신청 + 새 판) */
    public int pendingCount(long creatorId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM emoticon_reviews WHERE creator_id = ? AND status = 'PENDING'", Integer.class, creatorId);
        return count == null ? 0 : count;
    }

    /**
     * 같은 작가의 심사 요청을 줄 세운다 — 대기 건 수(상한)를 세고 새 건을 넣는 사이에 다른 요청이 끼지 못하게(트랜잭션 끝까지, PostgreSQL 어드바이저리 잠금 — 표 행이 없는 「작가」 단위라서).
     */
    public void lockCreator(long creatorId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('emoticon-review:' || ?::text, 0))", rs -> {
        }, creatorId);
    }

    /** 목록 — 조건은 부르는 쪽이 만든다(심사 대기·새 판은 오래 기다린 것이 위, 반려·전체는 최근이 위) */
    public List<ReviewRow> list(EmoticonReviewStatus status, EmoticonReviewKind kind, boolean oldestFirst) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + FROM + " WHERE true");
        List<Object> args = new java.util.ArrayList<>();
        if (status != null) {
            sql.append(" AND r.status = ?");
            args.add(status.name());
        }
        if (kind != null) {
            sql.append(" AND r.kind = ?");
            args.add(kind.name());
        }
        sql.append(oldestFirst ? " ORDER BY r.submitted_at, r.id" : " ORDER BY r.submitted_at DESC, r.id DESC").append(" LIMIT 500");
        return jdbc.query(sql.toString(), EmoticonReviewStore::rowOf, args.toArray());
    }

    /** 탭 숫자 — 처음 신청 대기 · 새 판 대기 · 반려 */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("PENDING", 0);
        out.put("UPDATE", 0);
        out.put("REJECTED", 0);
        jdbc.query("SELECT CASE WHEN status = 'REJECTED' THEN 'REJECTED' WHEN kind = 'UPDATE' THEN 'UPDATE' ELSE 'PENDING' END, count(*) FROM emoticon_reviews "
                + "WHERE status IN ('PENDING', 'REJECTED') GROUP BY 1", rs -> {
                    out.put(rs.getString(1), rs.getInt(2));
                });
        return out;
    }

    static ReviewRow rowOf(ResultSet rs, int i) throws SQLException {
        return new ReviewRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getObject(4, Long.class), rs.getLong(5), EmoticonReviewKind.valueOf(rs.getString(6)),
                EmoticonReviewStatus.valueOf(rs.getString(7)), EmoticonPackStore.instant(rs, 8), EmoticonPackStore.instant(rs, 9), EmoticonPackStore.instant(rs, 10),
                EmoticonPackStore.instant(rs, 11), rs.getString(12), EmoticonPackStore.instant(rs, 13), rs.getObject(14, Long.class), rs.getString(15), rs.getString(16),
                rs.getObject(17, Integer.class));
    }
}

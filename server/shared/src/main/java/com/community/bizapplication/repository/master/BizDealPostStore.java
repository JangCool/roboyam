package com.community.bizapplication.repository.master;

import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사업자 핫딜 게시 결과(V123 {@code biz_deal_posts}) — 신청 건 하나에 한 줄(PK). 넣기는 {@code ON CONFLICT DO NOTHING} 이라 다시 돌아도 두 번 생기지 않고, 이긴 쪽만 글을 만든다
 * (biz-hotdeal-contract 규칙 1).
 */
@Repository
public class BizDealPostStore {

    public static final String POSTED = "POSTED";
    public static final String FAILED = "FAILED";

    /** 게시 결과 한 줄 — {@code hiddenAt} 은 이 흐름이 숨긴 시각(그 글의 숨김 시각과 같을 때만 되돌리기가 푼다) */
    public record PostRow(long applicationId, Long postId, String status, String failReason, Instant hiddenAt, Instant createdAt) {

        public boolean posted() {
            return POSTED.equals(status);
        }
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BizDealPostStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<PostRow> find(long applicationId) {
        return jdbc.query("SELECT * FROM biz_deal_posts WHERE application_id = ?", (rs, i) -> row(rs), applicationId).stream().findFirst();
    }

    public Map<Long, PostRow> findAll(Collection<Long> applicationIds) {
        Map<Long, PostRow> out = new HashMap<>();
        if (!applicationIds.isEmpty()) {
            named.query("SELECT * FROM biz_deal_posts WHERE application_id IN (:ids)", new MapSqlParameterSource("ids", applicationIds), (ResultSet rs) -> {
                PostRow r = row(rs);
                out.put(r.applicationId(), r);
            });
        }
        return out;
    }

    /** 게시 성공 줄을 넣는다 — 이미 줄이 있으면(다른 실행이 먼저) false */
    public boolean claimPosted(long applicationId, long postId, Instant now) {
        return jdbc.update("INSERT INTO biz_deal_posts (application_id, post_id, status, created_at, updated_at) VALUES (?, ?, 'POSTED', ?, ?) ON CONFLICT DO NOTHING",
                applicationId, postId, ts(now), ts(now)) == 1;
    }

    /** 게시 실패 줄을 넣는다 — 이미 줄이 있으면 false */
    public boolean claimFailed(long applicationId, String reason, Instant now) {
        return jdbc.update("INSERT INTO biz_deal_posts (application_id, status, fail_reason, created_at, updated_at) VALUES (?, 'FAILED', ?, ?, ?) ON CONFLICT DO NOTHING",
                applicationId, reason, ts(now), ts(now)) == 1;
    }

    public void setHidden(long applicationId, Instant hiddenAt, Instant now) {
        jdbc.update("UPDATE biz_deal_posts SET hidden_at = ?, updated_at = ? WHERE application_id = ? AND status = 'POSTED'", ts(hiddenAt), ts(now), applicationId);
    }

    /** 게시할 차례 — 예약 · 핫딜 글 · 시작 ≤ 지금 &lt; 끝 · 게시 결과 없음(ID 키셋 — 처리하면 결과 줄이 생겨 다시 고르지 않는다) */
    public List<Long> due(Instant now, long afterId, int limit) {
        return jdbc.queryForList("SELECT a.id FROM biz_applications a WHERE a.kind = 'HOTDEAL_POST' AND a.status = 'SCHEDULED' AND a.start_at <= ? AND a.end_at > ? "
                + "AND a.id > ? AND NOT EXISTS (SELECT 1 FROM biz_deal_posts b WHERE b.application_id = a.id) ORDER BY a.id LIMIT ?", Long.class, ts(now), ts(now), afterId,
                limit);
    }

    /** 사업자 신원이 승인 상태인가(정지 · 승인 취소가 아님) — 게시 직전 재판정 */
    public boolean businessApproved(AppRow app) {
        if (app.businessProfileId() == null) {
            return false;
        }
        List<String> rows = jdbc.queryForList("SELECT status FROM business_profiles WHERE id = ?", String.class, app.businessProfileId());
        return !rows.isEmpty() && "APPROVED".equals(rows.get(0));
    }

    private static PostRow row(ResultSet rs) throws SQLException {
        Timestamp hidden = rs.getTimestamp("hidden_at");
        return new PostRow(rs.getLong("application_id"), (Long) rs.getObject("post_id", Long.class), rs.getString("status"), rs.getString("fail_reason"),
                hidden == null ? null : hidden.toInstant(), rs.getTimestamp("created_at").toInstant());
    }

    private static Timestamp ts(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }
}

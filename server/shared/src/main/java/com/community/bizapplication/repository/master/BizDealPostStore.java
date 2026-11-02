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
    /** 서비스 줄 {@code HOTDEAL} 정지로 실패 — 줄이 다시 승인되면 다시 고른다(결정 10) */
    public static final String SERVICE_SUSPENDED = "SERVICE_SUSPENDED";

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

    /**
     * 게시할 차례 — 예약 · 핫딜 글 · 시작 ≤ 지금 &lt; 끝 · 게시 결과 없음, 또는 서비스 정지({@code SERVICE_SUSPENDED})로 실패했는데 지금은 신원·서비스 줄 {@code HOTDEAL} 이 둘 다 승인
     * (결정 10 — 정지를 풀면 다시 올린다. 아직 정지면 고르지 않아 알림이 되풀이되지 않는다). ID 키셋 — 처리하면 결과 줄이 생기거나 바뀌어 다시 고르지 않는다.
     */
    public List<Long> due(Instant now, long afterId, int limit) {
        return jdbc.queryForList("SELECT a.id FROM biz_applications a WHERE a.kind = 'HOTDEAL_POST' AND a.status = 'SCHEDULED' AND a.start_at <= ? AND a.end_at > ? "
                + "AND a.id > ? AND (NOT EXISTS (SELECT 1 FROM biz_deal_posts b WHERE b.application_id = a.id) OR EXISTS (SELECT 1 FROM biz_deal_posts b "
                + "JOIN business_profiles p ON p.id = a.business_profile_id AND p.status = 'APPROVED' "
                + "JOIN business_services s ON s.profile_id = p.id AND s.service = 'HOTDEAL' AND s.status = 'APPROVED' "
                + "WHERE b.application_id = a.id AND b.status = 'FAILED' AND b.fail_reason = '" + SERVICE_SUSPENDED + "')) ORDER BY a.id LIMIT ?", Long.class, ts(now), ts(now),
                afterId, limit);
    }

    /** 서비스 정지로 실패한 줄을 지운다(다시 판정하기 전 — 신청 건 행을 잠근 뒤) — 지웠으면 true */
    public boolean clearServiceSuspended(long applicationId) {
        return jdbc.update("DELETE FROM biz_deal_posts WHERE application_id = ? AND status = 'FAILED' AND fail_reason = '" + SERVICE_SUSPENDED + "'", applicationId) == 1;
    }

    /**
     * 판매자 숨김(V127 — 광고주 일시 정지·중단) 한 줄 — 까닭(ADVERTISER_PAUSE·ADVERTISER_STOP) · 숨긴 시각 · 처리한 사람 표시 이름(운영자 이름 또는 사업자 닉네임 — 없으면 상호) ·
     * 운영자 대신 처리였나
     */
    public record SellerHidden(String cause, Instant at, String by, boolean byOperator) {
    }

    /** 글 ID → 판매자 숨김(지금 판매자 숨김 까닭으로 숨겨진 사업자 글만 — 관리자 핫딜 목록·상세 {@code sellerHidden}) */
    public Map<Long, SellerHidden> sellerHidden(Collection<Long> postIds) {
        Map<Long, SellerHidden> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        named.query("SELECT p.id, p.hidden_reason, p.hidden_at, a.takedown_actor, o.operator_name, pr.nickname, a.company_name, a.advertiser_name FROM posts p "
                        + "JOIN post_deals d ON d.post_id = p.id JOIN biz_applications a ON a.id = d.biz_application_id "
                        + "LEFT JOIN operators o ON a.takedown_actor = 'OPERATOR' AND o.id = a.taken_down_by LEFT JOIN profiles pr ON pr.user_id = a.user_id "
                        + "WHERE p.id IN (:ids) AND p.hidden_at IS NOT NULL AND p.hidden_until IS NULL AND p.hidden_reason IN ('SELLER_PAUSED', 'SELLER_STOPPED')",
                new MapSqlParameterSource("ids", postIds), (ResultSet rs) -> {
                    boolean operator = "OPERATOR".equals(rs.getString(4));
                    String member = rs.getString(6) != null ? rs.getString(6) : rs.getString(7) != null ? rs.getString(7) : rs.getString(8);
                    out.put(rs.getLong(1), new SellerHidden("SELLER_PAUSED".equals(rs.getString(2)) ? "ADVERTISER_PAUSE" : "ADVERTISER_STOP",
                            rs.getTimestamp(3).toInstant(), operator ? rs.getString(5) : member, operator));
                });
        return out;
    }

    /** 사업자 신원이 승인 상태인가(정지 · 승인 취소가 아님) — 게시 직전 재판정 */
    public boolean businessApproved(AppRow app) {
        if (app.businessProfileId() == null) {
            return false;
        }
        List<String> rows = jdbc.queryForList("SELECT status FROM business_profiles WHERE id = ?", String.class, app.businessProfileId());
        return !rows.isEmpty() && "APPROVED".equals(rows.get(0));
    }

    /** 서비스 줄 {@code HOTDEAL} 이 승인 상태인가(결정 10 — 신원은 승인이어도 이 줄만 정지일 수 있다) */
    public boolean hotdealServiceApproved(AppRow app) {
        if (app.businessProfileId() == null) {
            return false;
        }
        List<String> rows = jdbc.queryForList("SELECT status FROM business_services WHERE profile_id = ? AND service = 'HOTDEAL'", String.class, app.businessProfileId());
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

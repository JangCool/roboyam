package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonImageFormat;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 읽기 전용 질의 — 카탈로그 · 상점 목록(거르기·정렬·쪽) · 토큰 검사의 「로보티콘 → 팩」 · 회원(자격·닉네임) · 받기 보상 합계. 정렬·쪽은 DB 에서 한다.
 * Native SQL 인 이유: 상점의 판(live)·배열 태그·원장 줄을 한 질의로 엮는다(받기 보상은 포인트 원장 {@code point_ledger} 의 사유 {@code EMOTICON_CREATOR_REWARD} 줄 —
 * 원장이 JDBC 표라 같은 방식으로 읽는다). 시험이 스프링 문맥 없이 조립한다.
 */
@Repository
public class EmoticonReadStore {

    /** 원장 사유(포인트 {@code PointReason.EMOTICON_CREATOR_REWARD}) · 대상(받기 줄) — 포인트 쪽 이름과 같다 */
    public static final String REWARD_REASON = "EMOTICON_CREATOR_REWARD";
    public static final String REWARD_REF = "EMOTICON_ACQUIRE";

    /** 카탈로그 한 줄 — 상점에 나왔던 팩(판매 중 · 판매 중단)의 상점 판에 든 로보티콘 */
    public record CatalogRow(long emoticonId, long packId, String label, String storageKey, String staticPath, EmoticonImageFormat format, int frames) {
    }

    /** 회원 상태(자격 판정) */
    public record MemberState(long userId, String status, boolean emailVerified, boolean deleted) {

        public boolean gone() {
            return deleted || "WITHDRAWN".equals(status);
        }

        public boolean suspended() {
            return !"ACTIVE".equals(status);
        }
    }

    /** 회원 한 사람의 표시(닉네임 · 탈퇴) */
    public record MemberRef(long userId, String nickname, boolean withdrawn) {
    }

    /** 상점 목록 조건 — null 이면 그 거르기 없음. {@code after} 는 다음 쪽 기준(정렬 값, ID) */
    public record ShopQuery(EmoticonPackSource source, Boolean free, boolean latest, String q, Long afterValue, Long afterId, int limit) {
    }

    /** 받기 보상 한 줄 */
    public record RewardRow(long ledgerId, long packId, int points, Instant createdAt) {
    }

    private final JdbcTemplate jdbc;

    public EmoticonReadStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 카탈로그 · 토큰 ─────────────────────────────────────────────────────

    /** 카탈로그 — 팩 ID → 차례 */
    public List<CatalogRow> catalog() {
        return jdbc.query("SELECT i.emoticon_id, p.id, i.label, m.storage_key, m.static_path, m.format, m.frames FROM emoticon_packs p "
                + "JOIN emoticon_revision_items i ON i.revision_id = p.live_revision_id JOIN emoticon_images m ON m.id = i.image_id "
                + "WHERE p.status IN ('APPROVED', 'SUSPENDED') ORDER BY p.id, i.position", (rs, i) -> new CatalogRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getString(5), EmoticonImageFormat.valueOf(rs.getString(6)), rs.getInt(7)));
    }

    /** 이 로보티콘 ID 들 중 카탈로그에 있는 것 → 팩(토큰 검사 — 없는 ID 는 빠진다) */
    public Map<Long, Long> packsOfEmoticons(Collection<Long> emoticonIds) {
        Map<Long, Long> out = new HashMap<>();
        if (emoticonIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT i.emoticon_id, p.id FROM emoticon_revision_items i JOIN emoticon_packs p ON p.live_revision_id = i.revision_id "
                + "WHERE p.status IN ('APPROVED', 'SUSPENDED') AND i.emoticon_id IN (" + EmoticonPackStore.marks(emoticonIds.size()) + ")", rs -> {
                    out.put(rs.getLong(1), rs.getLong(2));
                }, emoticonIds.toArray());
        return out;
    }

    // ── 상점 ────────────────────────────────────────────────────────────────

    /**
     * 판매 중인 팩 한 쪽 — 인기(받은 수 ↓, ID ↓) 또는 최신(처음 나온 시각 ↓, ID ↓). 찾기 {@code q} 는 상점 판 이름 · 태그 · 작가 닉네임(대소문자 무시, 부분).
     * 다음 쪽은 (정렬 값, ID) 다음부터(키셋 — 최신은 처음 나온 시각의 마이크로초).
     *
     * @return 팩 ID 차례
     */
    public List<Long> shopPage(ShopQuery query) {
        StringBuilder sql = new StringBuilder("SELECT p.id FROM emoticon_packs p JOIN emoticon_pack_revisions r ON r.id = p.live_revision_id "
                + "LEFT JOIN profiles pr ON pr.user_id = p.creator_id WHERE p.status = 'APPROVED'");
        List<Object> args = new ArrayList<>();
        if (query.source() != null) {
            sql.append(" AND p.source = ?");
            args.add(query.source().name());
        }
        if (query.free() != null) {
            sql.append(query.free() ? " AND p.price = 0" : " AND p.price > 0");
        }
        if (query.q() != null) {
            String like = "%" + query.q().toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (lower(r.name) LIKE ? OR lower(coalesce(pr.nickname, '')) LIKE ? OR EXISTS (SELECT 1 FROM unnest(r.tags) t WHERE lower(t) LIKE ?))");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String key = query.latest() ? "(floor(extract(epoch FROM p.published_at) * 1000000))::bigint" : "p.acquire_count::bigint";
        if (query.afterValue() != null && query.afterId() != null) {
            sql.append(" AND (").append(key).append(", p.id) < (?, ?)");
            args.add(query.afterValue());
            args.add(query.afterId());
        }
        sql.append(" ORDER BY ").append(key).append(" DESC, p.id DESC LIMIT ?");
        args.add(query.limit());
        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    /** 다음 쪽 기준 값 — 인기 = 받은 수, 최신 = 처음 나온 시각(마이크로초) */
    public long sortValue(long packId, boolean latest) {
        String key = latest ? "(floor(extract(epoch FROM published_at) * 1000000))::bigint" : "acquire_count::bigint";
        Long value = jdbc.queryForObject("SELECT " + key + " FROM emoticon_packs WHERE id = ?", Long.class, packId);
        return value == null ? 0 : value;
    }

    // ── 관리자 팩 목록 ──────────────────────────────────────────────────────

    /**
     * 관리자 팩 목록(임시 제외) — 운영 팩이 위, 그다음 최근 고친 순. 찾기 {@code q} 는 보이는 판(상점 판, 없으면 마지막으로 낸 판)의 이름 · 태그 · 작가 닉네임.
     *
     * @return 팩 ID 차례(최대 500)
     */
    public List<Long> adminPacks(EmoticonPackSource source, EmoticonPackStatus status, String q, Long creatorId) {
        StringBuilder sql = new StringBuilder("SELECT p.id FROM emoticon_packs p LEFT JOIN LATERAL (SELECT r.name, r.tags FROM emoticon_pack_revisions r WHERE r.pack_id = p.id "
                + "AND r.version IS NOT NULL ORDER BY (r.id = p.live_revision_id) DESC, r.version DESC LIMIT 1) d ON true LEFT JOIN profiles pr ON pr.user_id = p.creator_id "
                + "WHERE p.status <> 'DRAFT'");
        List<Object> args = new ArrayList<>();
        if (source != null) {
            sql.append(" AND p.source = ?");
            args.add(source.name());
        }
        if (status != null) {
            sql.append(" AND p.status = ?");
            args.add(status.name());
        }
        if (creatorId != null) {
            sql.append(" AND p.creator_id = ?");
            args.add(creatorId);
        }
        if (q != null) {
            String like = "%" + q.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (lower(coalesce(d.name, '')) LIKE ? OR lower(coalesce(pr.nickname, '')) LIKE ? OR EXISTS (SELECT 1 FROM unnest(d.tags) t WHERE lower(t) LIKE ?))");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY (p.source = 'OFFICIAL') DESC, p.updated_at DESC, p.id DESC LIMIT 500");
        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    public Optional<MemberState> member(long userId) {
        return jdbc.query("SELECT id, status, email_verified_at IS NOT NULL, deleted_at IS NOT NULL FROM users WHERE id = ?",
                (rs, i) -> new MemberState(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4)), userId).stream().findFirst();
    }

    /** 회원 → 닉네임·탈퇴(없는 회원은 빠진다) */
    public Map<Long, MemberRef> members(Collection<Long> userIds) {
        Map<Long, MemberRef> out = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT u.id, p.nickname, u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE u.id IN ("
                + EmoticonPackStore.marks(userIds.size()) + ")", rs -> {
                    out.put(rs.getLong(1), new MemberRef(rs.getLong(1), rs.getString(2), rs.getBoolean(3)));
                }, userIds.toArray());
        return out;
    }

    // ── 받기 보상(포인트 원장) ──────────────────────────────────────────────

    /** 팩마다 작가에게 준 받기 보상 합계 */
    public Map<Long, Long> rewardTotals(Collection<Long> packIds) {
        Map<Long, Long> out = new HashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT a.pack_id, coalesce(sum(l.delta), 0) FROM point_ledger l JOIN emoticon_acquisitions a ON a.id = l.ref_id WHERE l.reason = '" + REWARD_REASON
                + "' AND l.ref_type = '" + REWARD_REF + "' AND a.pack_id IN (" + EmoticonPackStore.marks(packIds.size()) + ") GROUP BY a.pack_id", rs -> {
                    out.put(rs.getLong(1), rs.getLong(2));
                }, packIds.toArray());
        return out;
    }

    /** 작가가 받은 보상 합계 · {@code since} 뒤 합계 */
    public long rewardSum(long creatorId, Instant since) {
        Long sum = since == null
                ? jdbc.queryForObject("SELECT coalesce(sum(delta), 0) FROM point_ledger WHERE user_id = ? AND reason = '" + REWARD_REASON + "'", Long.class, creatorId)
                : jdbc.queryForObject("SELECT coalesce(sum(delta), 0) FROM point_ledger WHERE user_id = ? AND reason = '" + REWARD_REASON + "' AND created_at >= ?",
                        Long.class, creatorId, Timestamp.from(since));
        return sum == null ? 0 : sum;
    }

    /** 최근 보상(최근 것 먼저) */
    public List<RewardRow> recentRewards(long creatorId, int limit) {
        return jdbc.query("SELECT l.id, a.pack_id, l.delta, l.created_at FROM point_ledger l JOIN emoticon_acquisitions a ON a.id = l.ref_id WHERE l.user_id = ? "
                + "AND l.reason = '" + REWARD_REASON + "' AND l.ref_type = '" + REWARD_REF + "' ORDER BY l.id DESC LIMIT ?", (rs, i) -> new RewardRow(rs.getLong(1),
                rs.getLong(2), rs.getInt(3), rs.getTimestamp(4).toInstant()), creatorId, limit);
    }

    /** 작가의 팩 수(임시 제외) — 관리자 회원 상세 한 줄 */
    public int creatorPackCount(long creatorId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM emoticon_packs WHERE creator_id = ? AND status <> 'DRAFT'", Integer.class, creatorId);
        return count == null ? 0 : count;
    }
}

package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonImageFormat;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.QEmoticonAcquisitionEntity;
import com.community.emoticon.entity.QEmoticonImageEntity;
import com.community.emoticon.entity.QEmoticonPackEntity;
import com.community.emoticon.entity.QEmoticonRevisionItemEntity;
import com.community.point.entity.QPointLedgerEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 읽기 전용 질의 — 카탈로그 · 상점 목록(거르기·정렬·쪽) · 토큰 검사의 「로보티콘 → 팩」 · 회원(자격·닉네임) · 받기 보상 합계. 정렬·쪽은 DB 에서 한다.
 *
 * <p>QueryDSL 이다({@link EmoticonStoreSupport}) — 회원·프로필·포인트 원장({@code point_ledger} 의 사유 {@code EMOTICON_CREATOR_REWARD} 줄)은 엔티티로 읽는다.
 * Native SQL 로 남긴 것과 이유: 상점 목록({@link #shopPage} — 배열 태그 찾기 {@code unnest(tags) LIKE} · 행 값 키셋 {@code (정렬 값, id) < (?, ?)} · 처음 나온 시각의 마이크로초 정렬 값),
 * 관리자 팩 목록({@link #adminPacks} — 보이는 판을 고르는 {@code LEFT JOIN LATERAL … LIMIT 1} · 배열 태그 찾기).
 */
@Repository
public class EmoticonReadStore extends EmoticonStoreSupport {

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

    private static final QEmoticonPackEntity P = QEmoticonPackEntity.emoticonPackEntity;
    private static final QEmoticonRevisionItemEntity I = QEmoticonRevisionItemEntity.emoticonRevisionItemEntity;
    private static final QEmoticonImageEntity M = QEmoticonImageEntity.emoticonImageEntity;
    private static final QEmoticonAcquisitionEntity A = QEmoticonAcquisitionEntity.emoticonAcquisitionEntity;
    private static final QPointLedgerEntity L = QPointLedgerEntity.pointLedgerEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;

    public EmoticonReadStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 카탈로그 · 토큰 ─────────────────────────────────────────────────────

    /** 카탈로그 — 팩 ID → 차례 */
    public List<CatalogRow> catalog() {
        return query.select(I.emoticonId, P.id, I.label, M.storageKey, M.staticPath, M.format, M.frames).from(P).join(I).on(I.revisionId.eq(P.liveRevisionId))
                .join(M).on(M.id.eq(I.imageId)).where(EmoticonPackStore.published(P)).orderBy(P.id.asc(), I.position.asc()).fetch().stream()
                .map(t -> new CatalogRow(t.get(I.emoticonId), t.get(P.id), t.get(I.label), t.get(M.storageKey), t.get(M.staticPath), t.get(M.format), t.get(M.frames)))
                .collect(Collectors.toList());
    }

    /** 이 로보티콘 ID 들 중 카탈로그에 있는 것 → 팩(토큰 검사 — 없는 ID 는 빠진다) */
    public Map<Long, Long> packsOfEmoticons(Collection<Long> emoticonIds) {
        Map<Long, Long> out = new HashMap<>();
        if (emoticonIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(I.emoticonId, P.id).from(I).join(P).on(P.liveRevisionId.eq(I.revisionId))
                .where(EmoticonPackStore.published(P), I.emoticonId.in(emoticonIds)).fetch()) {
            out.put(t.get(I.emoticonId), t.get(P.id));
        }
        return out;
    }

    // ── 상점 ────────────────────────────────────────────────────────────────

    /**
     * 판매 중인 팩 한 쪽 — 인기(받은 수 ↓, ID ↓) 또는 최신(처음 나온 시각 ↓, ID ↓). 찾기 {@code q} 는 상점 판 이름 · 태그 · 작가 닉네임(대소문자 무시, 부분).
     * 다음 쪽은 (정렬 값, ID) 다음부터(키셋 — 최신은 처음 나온 시각의 마이크로초).
     *
     * <p>Native SQL 인 이유: 태그 배열 찾기({@code EXISTS (SELECT 1 FROM unnest(r.tags) t WHERE lower(t) LIKE ?)}) · 행 값 비교 키셋 {@code (정렬 값, id) < (?, ?)}
     * (정렬 색인 {@code ix_emoticon_packs_shop} 을 그대로 쓴다) · 정렬 값 {@code floor(extract(epoch …) * 1000000)} — 다음 쪽 기준({@link #sortValue})과 같은 값이어야 한다.
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
            String like = like(query.q());
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

    /**
     * 다음 쪽 기준 값 — 인기 = 받은 수, 최신 = 처음 나온 시각(마이크로초 — {@link #shopPage} 의 {@code floor(extract(epoch …) * 1000000)} 와 같은 값: DB 시각은 마이크로초 단위다).
     * 팩이 없거나 처음 나온 시각이 없으면 0.
     */
    public long sortValue(long packId, boolean latest) {
        if (!latest) {
            Integer count = query.select(P.acquireCount).from(P).where(P.id.eq(packId)).fetchFirst();
            return count == null ? 0 : count;
        }
        Instant at = query.select(P.publishedAt).from(P).where(P.id.eq(packId)).fetchFirst();
        return at == null ? 0 : Math.addExact(Math.multiplyExact(at.getEpochSecond(), 1_000_000L), at.getNano() / 1_000);
    }

    // ── 관리자 팩 목록 ──────────────────────────────────────────────────────

    /**
     * 관리자 팩 목록(임시 제외) — 운영 팩이 위, 그다음 최근 고친 순. 찾기 {@code q} 는 보이는 판(상점 판, 없으면 마지막으로 낸 판)의 이름 · 태그 · 작가 닉네임.
     *
     * <p>Native SQL 인 이유: 팩마다 보이는 판 하나를 고르는 {@code LEFT JOIN LATERAL (… ORDER BY (r.id = p.live_revision_id) DESC, r.version DESC LIMIT 1)} 과
     * 태그 배열 찾기({@code unnest(d.tags) LIKE}).
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
            String like = like(q);
            sql.append(" AND (lower(coalesce(d.name, '')) LIKE ? OR lower(coalesce(pr.nickname, '')) LIKE ? OR EXISTS (SELECT 1 FROM unnest(d.tags) t WHERE lower(t) LIKE ?))");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY (p.source = 'OFFICIAL') DESC, p.updated_at DESC, p.id DESC LIMIT 500");
        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    private static String like(String q) {
        return "%" + q.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    public Optional<MemberState> member(long userId) {
        Tuple t = query.select(U.id, U.status, U.emailVerifiedAt, U.deletedAt).from(U).where(U.id.eq(userId)).fetchFirst();
        return Optional.ofNullable(t).map(r -> new MemberState(r.get(U.id), r.get(U.status).name(), r.get(U.emailVerifiedAt) != null, r.get(U.deletedAt) != null));
    }

    /** 회원 → 닉네임·탈퇴(없는 회원은 빠진다) */
    public Map<Long, MemberRef> members(Collection<Long> userIds) {
        Map<Long, MemberRef> out = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(U.id, PR.nickname, U.status, U.deletedAt).from(U).leftJoin(PR).on(PR.userId.eq(U.id)).where(U.id.in(userIds)).fetch()) {
            boolean withdrawn = t.get(U.status) == UserStatus.WITHDRAWN || t.get(U.deletedAt) != null;
            out.put(t.get(U.id), new MemberRef(t.get(U.id), t.get(PR.nickname), withdrawn));
        }
        return out;
    }

    // ── 받기 보상(포인트 원장) ──────────────────────────────────────────────

    /** 받기 보상 줄(사유 · 대상 = 받기 줄) */
    private static BooleanExpression reward() {
        return L.reason.eq(REWARD_REASON).and(L.refType.eq(REWARD_REF));
    }

    /** 팩마다 작가에게 준 받기 보상 합계 */
    public Map<Long, Long> rewardTotals(Collection<Long> packIds) {
        Map<Long, Long> out = new HashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        NumberExpression<Long> sum = sumLong(L.delta);
        for (Tuple t : query.select(A.packId, sum).from(L).join(A).on(A.id.eq(L.refId)).where(reward(), A.packId.in(packIds)).groupBy(A.packId).fetch()) {
            out.put(t.get(A.packId), n(t.get(sum)));
        }
        return out;
    }

    /** 작가가 받은 보상 합계 · {@code since} 뒤 합계 */
    public long rewardSum(long creatorId, Instant since) {
        BooleanExpression where = L.userId.eq(creatorId).and(L.reason.eq(REWARD_REASON));
        if (since != null) {
            where = where.and(L.createdAt.goe(since));
        }
        return n(query.select(sumLong(L.delta)).from(L).where(where).fetchFirst());
    }

    /** 최근 보상(최근 것 먼저) */
    public List<RewardRow> recentRewards(long creatorId, int limit) {
        return query.select(L.id, A.packId, L.delta, L.createdAt).from(L).join(A).on(A.id.eq(L.refId)).where(L.userId.eq(creatorId), reward()).orderBy(L.id.desc())
                .limit(limit).fetch().stream().map(t -> new RewardRow(t.get(L.id), t.get(A.packId), t.get(L.delta), t.get(L.createdAt))).collect(Collectors.toList());
    }

    /** 작가의 팩 수(임시 제외) — 관리자 회원 상세 한 줄 */
    public int creatorPackCount(long creatorId) {
        return (int) n(query.select(P.count()).from(P).where(P.creatorId.eq(creatorId), P.status.ne(EmoticonPackStatus.DRAFT)).fetchFirst());
    }
}

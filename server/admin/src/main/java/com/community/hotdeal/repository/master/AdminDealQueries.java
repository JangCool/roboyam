package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.entity.QDealOgImageEntity;
import com.community.hotdeal.entity.QPostDealEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.Ilike;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 핫딜 글·의심 후보·영향 미리 보기 질의(hotdeal-contract 「API 후보」 관리자). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>읽을 때 계산하는 딜 상태(종료 · 기간 종료 = 마감 또는 등록 + 설정 일수 · 의심)를 상태별 건수와 함께 한 번에 세고, 글 표({@code posts} — 파티션 표라 연관이 없어
 * {@code join(..).on(..)} 으로 잇는다)·딜 표·프로필을 함께 거른다. 상태 판정은 shared {@code DealViews#view} 와 같다(ENDED &gt; EXPIRED &gt; FLAGGED &gt; ACTIVE) — 한쪽을 고치면 같이 고친다.
 * 「살아 있는 글」 = 지우지 않음 · 공개 · 예약 삭제 전(숨긴 글은 든다 — 관리자 목록, {@link #alivePost}), 「공개 글」은 숨김도 아니고 일반 글({@link #publicPost} — shared {@code DealQueries.PUBLIC_POST}).
 *
 * <p>위장 홍보 의심 후보({@code deal_suspicions})만 네이티브 SQL 이다 — 엔티티가 없고 글 ID 배열({@code post_ids bigint[]})을 {@code = ANY(..)} 로 풀어 마지막 글 시각을 센다
 * (JPQL 에 배열 연산이 없다).
 */
@Repository
public class AdminDealQueries extends IrobitRepositoryQDSL {

    private static final QPostDealEntity DEAL = QPostDealEntity.postDealEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QDealOgImageEntity OG = QDealOgImageEntity.dealOgImageEntity;

    public static final String ALL = "ALL";

    private final JdbcTemplate jdbc;

    public AdminDealQueries(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 목록 조건(상태·차단 조건 제외).
     *
     * @param boardIds 볼 핫딜 게시판(고른 하나 또는 모든 핫딜 게시판 — 비면 아무 글도 없다)
     * @param q        제목·쇼핑몰·작성자 닉네임 부분 일치(대소문자 무시, null 이면 없음)
     * @param source   출처 MEMBER · BUSINESS(V123 — null 이면 전체)
     */
    public record Filter(List<Long> boardIds, String q, Instant from, Instant to, String source) {
    }

    /** 목록 한 줄의 글 칸 */
    public record Row(long postId, String title, long boardId, Long prefixId, Long authorId, Instant createdAt, int likeCount, boolean publicPost) {
    }

    /** 한 쪽 */
    public record Page(long total, List<Row> rows) {
    }

    // ── 글 조건(대시보드 가젯도 쓴다) ────────────────────────────────────────

    /** 살아 있는 글(관리자 목록 — 숨김 포함): 지우지 않음 · 공개 · 예약 삭제 전 */
    public static BooleanExpression alivePost(QPostEntity p, Instant now) {
        return PostRepositoryQDSL.alive(p, now);
    }

    /** 공개 글: 살아 있고 숨김 중이 아니며 일반 글(소모임 글 아님) */
    public static BooleanExpression publicPost(QPostEntity p, Instant now) {
        return alivePost(p, now).and(PostRepositoryQDSL.notHidden(p, now)).and(p.groupId.isNull());
    }

    /**
     * 진행 중(ACTIVE·FLAGGED — 종료도 기간 종료도 아님). shared {@code DealQueries.LIVE_DEAL} 과 같다.
     *
     * @param expireFrom 등록이 이 시각 뒤여야 기간 종료가 아니다(마감 없는 딜, {@code autoExpireDays} 가 0 이면 보지 않는다)
     */
    public static BooleanExpression liveDeal(QPostDealEntity d, Instant now, int autoExpireDays, Instant expireFrom) {
        BooleanExpression live = d.status.eq(PostDealEntity.ACTIVE).and(d.endsAt.isNull().or(d.endsAt.gt(now)));
        return autoExpireDays == 0 ? live : live.and(d.endsAt.isNotNull().or(d.createdAt.gt(expireFrom)));
    }

    private static BooleanExpression liveDeal(int autoExpireDays, Instant now) {
        return liveDeal(DEAL, now, autoExpireDays, now.minus(Duration.ofDays(autoExpireDays)));
    }

    // ── 핫딜 글 목록 ────────────────────────────────────────────────────────

    /** 진행 중(저장 상태 ACTIVE)인 딜이 가는 호스트들(상품·최종 — 차단 판정용, 중복 없음) */
    public List<String> activeHosts(Collection<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        // JPQL 에 UNION 이 없어 두 번 읽어 합친다(중복 없음은 같다)
        Set<String> hosts = new LinkedHashSet<>(query.select(DEAL.productHost).distinct().from(DEAL)
                .where(DEAL.status.eq(PostDealEntity.ACTIVE), DEAL.boardId.in(boardIds)).fetch());
        hosts.addAll(query.select(DEAL.finalHost).distinct().from(DEAL)
                .where(DEAL.status.eq(PostDealEntity.ACTIVE), DEAL.finalHost.isNotNull(), DEAL.boardId.in(boardIds)).fetch());
        return new ArrayList<>(hosts);
    }

    /**
     * 한 쪽(최신 등록순 — 글 ID 큰 차례).
     *
     * @param view         ACTIVE · FLAGGED · ENDED · EXPIRED · ALL
     * @param blockedHosts 차단 도메인 진행 중만 볼 때 그 호스트들(null 이면 거르지 않는다)
     */
    public Page search(Filter f, String view, Collection<String> blockedHosts, int autoExpireDays, Instant now, long offset, int limit) {
        BooleanBuilder w = where(f, now);
        if (w != null) {
            w.and(view(view, autoExpireDays, now));
        }
        if (w != null && blockedHosts != null) {
            w = blocked(w, blockedHosts, autoExpireDays, now);
        }
        if (w == null) {
            return new Page(0, List.of());
        }
        Long total = from(query.select(DEAL.postId.count())).where(w).fetchOne();
        List<Row> rows = from(query.select(rowColumns(now))).where(w).orderBy(DEAL.postId.desc()).offset(offset).limit(limit).fetch()
                .stream().map(AdminDealQueries::toRow).toList();
        return new Page(total == null ? 0 : total, rows);
    }

    /** 상태별 건수(상태 조건만 뺀 같은 조건 — 차단 거르기를 켰으면 그것까지) — ACTIVE·FLAGGED·ENDED·EXPIRED·ALL */
    public Map<String, Long> counts(Filter f, Collection<String> blockedHosts, int autoExpireDays, Instant now) {
        BooleanBuilder w = where(f, now);
        if (w != null && blockedHosts != null) {
            w = blocked(w, blockedHosts, autoExpireDays, now);
        }
        Map<String, Long> out = new LinkedHashMap<>();
        if (w == null) {
            for (String key : List.of("ACTIVE", "FLAGGED", "ENDED", "EXPIRED", ALL)) {
                out.put(key, 0L);
            }
            return out;
        }
        BooleanExpression expired = expired(autoExpireDays, now);
        BooleanExpression active = DEAL.status.eq(PostDealEntity.ACTIVE);
        NumberExpression<Long> activeCount = countIf(active.and(expired.not()).and(DEAL.flaggedAt.isNull()));
        NumberExpression<Long> flaggedCount = countIf(active.and(expired.not()).and(DEAL.flaggedAt.isNotNull()));
        NumberExpression<Long> endedCount = countIf(DEAL.status.eq(PostDealEntity.ENDED));
        NumberExpression<Long> expiredCount = countIf(active.and(expired));
        NumberExpression<Long> all = DEAL.postId.count();
        Tuple t = from(query.select(activeCount, flaggedCount, endedCount, expiredCount, all)).where(w).fetchOne();
        out.put("ACTIVE", nz(t == null ? null : t.get(activeCount)));
        out.put("FLAGGED", nz(t == null ? null : t.get(flaggedCount)));
        out.put("ENDED", nz(t == null ? null : t.get(endedCount)));
        out.put("EXPIRED", nz(t == null ? null : t.get(expiredCount)));
        out.put(ALL, nz(t == null ? null : t.get(all)));
        return out;
    }

    /** 차단 도메인 진행 중 건수(상태·차단 조건을 뺀 같은 조건에서) */
    public long blockedActiveCount(Filter f, Collection<String> blockedHosts, int autoExpireDays, Instant now) {
        if (blockedHosts.isEmpty()) {
            return 0;
        }
        BooleanBuilder w = where(f, now);
        if (w == null) {
            return 0;
        }
        w = blocked(w, blockedHosts, autoExpireDays, now);
        return nz(from(query.select(DEAL.postId.count())).where(w).fetchOne());
    }

    /** 살아 있는 핫딜 게시판 글 한 줄(상세 — 딜 줄이 있어야) */
    public Row row(long postId, Collection<Long> boardIds, Instant now) {
        if (boardIds.isEmpty()) {
            return null;
        }
        BooleanBuilder w = where(new Filter(List.copyOf(boardIds), null, null, null, null), now);
        w.and(POST.id.eq(postId));
        Tuple t = from(query.select(rowColumns(now))).where(w).fetchFirst();
        return t == null ? null : toRow(t);
    }

    /** 글 ID → 지금 게시판 ID(지운 글 포함) */
    public Map<Long, Long> postBoards(Collection<Long> postIds) {
        Map<Long, Long> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(POST.id, POST.boardId).from(POST).where(POST.id.in(postIds)).fetch()) {
            out.put(t.get(POST.id), t.get(POST.boardId));
        }
        return out;
    }

    /** OG 사본의 원본 주소(사본 ID → 주소) */
    public Map<Long, String> ogOrigins(Collection<Long> ogImageIds) {
        Map<Long, String> out = new HashMap<>();
        if (ogImageIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(OG.id, OG.originUrl).from(OG).where(OG.id.in(ogImageIds)).fetch()) {
            out.put(t.get(OG.id), t.get(OG.originUrl));
        }
        return out;
    }

    /** 같은 딜(진행 중 · 공개 글 · 이 글 제외, 최신 차례) — 글 ID·제목·딜가·등록 시각 */
    public record SameRow(long postId, String title, long dealPrice, Instant createdAt) {
    }

    public List<SameRow> sameDeals(String key, long excludePostId, int autoExpireDays, Instant now, int limit) {
        return query.select(DEAL.postId, POST.title, DEAL.dealPrice, DEAL.createdAt).from(DEAL).join(POST).on(POST.id.eq(DEAL.postId))
                .where(DEAL.productUrlKey.eq(key), DEAL.postId.ne(excludePostId), liveDeal(autoExpireDays, now), publicPost(POST, now))
                .orderBy(DEAL.postId.desc()).limit(limit).fetch().stream()
                .map(t -> new SameRow(t.get(DEAL.postId), t.get(POST.title), nz(t.get(DEAL.dealPrice)), t.get(DEAL.createdAt))).toList();
    }

    // ── 영향 미리 보기 ──────────────────────────────────────────────────────

    /** 진행 중(ACTIVE·FLAGGED) 공개 글의 딜 — 영향 미리 보기·제휴 방식 전환 셈 */
    public record LiveDeal(long postId, String title, String storeName, String productHost, String finalHost, boolean affiliate) {
    }

    /** 상품·최종 호스트가 이 도메인(하위 도메인 포함)인 진행 중 공개 글 수와 최근 것 몇 개 */
    public record DomainImpact(long count, List<LiveDeal> samples) {
    }

    public DomainImpact domainImpact(String domain, int autoExpireDays, Instant now, int sampleMax) {
        String suffix = "%." + domain;
        BooleanExpression where = DEAL.productHost.eq(domain).or(DEAL.productHost.like(suffix)).or(DEAL.finalHost.eq(domain)).or(DEAL.finalHost.like(suffix))
                .and(liveDeal(autoExpireDays, now)).and(publicPost(POST, now));
        Long n = query.select(DEAL.postId.count()).from(DEAL).join(POST).on(POST.id.eq(DEAL.postId)).where(where).fetchOne();
        List<LiveDeal> samples = query.select(DEAL.postId, POST.title, DEAL.storeName, DEAL.productHost, DEAL.finalHost, DEAL.affiliate)
                .from(DEAL).join(POST).on(POST.id.eq(DEAL.postId)).where(where).orderBy(DEAL.postId.desc()).limit(sampleMax).fetch().stream()
                .map(t -> new LiveDeal(t.get(DEAL.postId), t.get(POST.title), t.get(DEAL.storeName), t.get(DEAL.productHost), t.get(DEAL.finalHost),
                        Boolean.TRUE.equals(t.get(DEAL.affiliate)))).toList();
        return new DomainImpact(nz(n), samples);
    }

    /** 진행 중 공개 글의 딜 전부(제휴 방식 전환 셈 — 호스트·개인 제휴 표시만) */
    public List<LiveDeal> liveDeals(int autoExpireDays, Instant now) {
        return query.select(DEAL.postId, DEAL.storeName, DEAL.productHost, DEAL.finalHost, DEAL.affiliate).from(DEAL).join(POST).on(POST.id.eq(DEAL.postId))
                .where(liveDeal(autoExpireDays, now), publicPost(POST, now)).fetch().stream()
                .map(t -> new LiveDeal(t.get(DEAL.postId), null, t.get(DEAL.storeName), t.get(DEAL.productHost), t.get(DEAL.finalHost),
                        Boolean.TRUE.equals(t.get(DEAL.affiliate)))).toList();
    }

    // ── 위장 홍보 의심 후보 ──────────────────────────────────────────────────

    /** 후보 한 줄(표 + 읽을 때 센 값) */
    public record SuspicionRow(long id, String kind, String status, Long memberId, String host, String storeName, int postCount, int memberCount,
                               Integer otherRatioPercent, int windowDays, Instant detectedAt, Instant lastPostAt, Long dismissedBy, Instant dismissedAt,
                               String note, List<Long> postIds) {
    }

    /** Native SQL — {@code deal_suspicions} 는 엔티티가 없고 {@code post_ids}(bigint[]) 를 {@code = ANY(..)} 로 푼다(JPQL 에 배열 연산이 없다) */
    private static final String SUSPICION_COLUMNS = "s.id, s.kind, s.status, s.member_id, s.host, s.store_name, s.post_count, s.member_count, s.other_ratio_percent, "
            + "s.window_days, s.detected_at, (SELECT max(d.created_at) FROM post_deals d WHERE d.post_id = ANY (s.post_ids)) AS last_post_at, s.dismissed_by, "
            + "s.dismissed_at, s.dismiss_note, s.post_ids";

    /** 상태(OPEN·DISMISSED)·종류(null = 둘 다)의 한 쪽 — 마지막 글이 최근인 것부터. Native SQL(위 사유) */
    public List<SuspicionRow> suspicions(String status, String kind, long offset, int limit) {
        List<Object> args = new ArrayList<>(List.of(status));
        String where = " WHERE s.status = ?";
        if (kind != null) {
            where += " AND s.kind = ?";
            args.add(kind);
        }
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + SUSPICION_COLUMNS + " FROM deal_suspicions s" + where + " ORDER BY last_post_at DESC NULLS LAST, s.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> suspicion(rs), args.toArray());
    }

    /** Native SQL(위 사유) */
    public SuspicionRow suspicion(long id) {
        List<SuspicionRow> rows = jdbc.query("SELECT " + SUSPICION_COLUMNS + " FROM deal_suspicions s WHERE s.id = ?", (rs, i) -> suspicion(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** (상태 → 종류 → 수). Native SQL — {@code deal_suspicions} 는 엔티티가 없다 */
    public Map<String, Map<String, Long>> suspicionCounts() {
        Map<String, Map<String, Long>> out = new HashMap<>();
        jdbc.query("SELECT status, kind, count(*) FROM deal_suspicions GROUP BY status, kind", rs -> {
            out.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), rs.getLong(3));
        });
        return out;
    }

    /** 같은 판매처에 기간 안 글을 올린 다른 회원 수(회원 후보 — 사업자 글 제외) */
    public int otherMembers(String host, long memberId, int windowDays, Instant now) {
        Long n = query.select(DEAL.authorId.countDistinct()).from(DEAL)
                .where(storeHost().eq(host), DEAL.source.eq(PostDealEntity.SOURCE_MEMBER), DEAL.authorId.isNotNull(), DEAL.authorId.ne(memberId),
                        DEAL.createdAt.goe(now.minus(Duration.ofDays(windowDays))))
                .fetchOne();
        return n == null ? 0 : n.intValue();
    }

    /** 후보 글 한 줄 */
    public record SuspicionPost(long postId, String title, Long boardId, Long authorId, long dealPrice, String status, Instant flaggedAt, Instant endsAt,
                                Instant createdAt) {
    }

    /** 후보의 글(최신 차례, 지운 글은 글 행이 있으면 든다) */
    public List<SuspicionPost> suspicionPosts(List<Long> postIds, int limit) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return query.select(DEAL.postId, POST.title, POST.boardId, DEAL.authorId, DEAL.dealPrice, DEAL.status, DEAL.flaggedAt, DEAL.endsAt, DEAL.createdAt)
                .from(DEAL).leftJoin(POST).on(POST.id.eq(DEAL.postId)).where(DEAL.postId.in(postIds)).orderBy(DEAL.postId.desc()).limit(limit).fetch().stream()
                .map(t -> new SuspicionPost(t.get(DEAL.postId), t.get(POST.title), t.get(POST.boardId), t.get(DEAL.authorId), nz(t.get(DEAL.dealPrice)),
                        t.get(DEAL.status), t.get(DEAL.flaggedAt), t.get(DEAL.endsAt), t.get(DEAL.createdAt))).toList();
    }

    /** 판매처 후보의 회원별 셈 — (회원 → [이 판매처 글 수, 다른 판매처 핫딜 수]) 기간 안, 사업자 글 제외 */
    public Map<Long, long[]> storeMembers(String host, Collection<Long> memberIds, int windowDays, Instant now) {
        Map<Long, long[]> out = new LinkedHashMap<>();
        if (memberIds.isEmpty()) {
            return out;
        }
        NumberExpression<Long> here = countIf(storeHost().eq(host));
        NumberExpression<Long> elsewhere = countIf(storeHost().ne(host));
        for (Tuple t : query.select(DEAL.authorId, here, elsewhere).from(DEAL)
                .where(DEAL.source.eq(PostDealEntity.SOURCE_MEMBER), DEAL.authorId.in(memberIds), DEAL.createdAt.goe(now.minus(Duration.ofDays(windowDays))))
                .groupBy(DEAL.authorId).orderBy(here.desc(), DEAL.authorId.asc()).fetch()) {
            out.put(t.get(DEAL.authorId), new long[] {nz(t.get(here)), nz(t.get(elsewhere))});
        }
        return out;
    }

    /** 열린 후보 무시(조건부 — 이미 무시됐으면 0). Native SQL — {@code deal_suspicions} 는 엔티티가 없다 */
    public int dismissSuspicion(long id, long operatorId, String note, Instant now) {
        return jdbc.update("UPDATE deal_suspicions SET status = 'DISMISSED', dismissed_at = ?, dismissed_by = ?, dismiss_note = ?, updated_at = ? WHERE id = ? AND status = 'OPEN'",
                ts(now), operatorId, note, ts(now), id);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private SuspicionRow suspicion(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Array arr = rs.getArray(16);
        List<Long> ids = new ArrayList<>();
        if (arr != null) {
            for (Object v : (Object[]) arr.getArray()) {
                ids.add(((Number) v).longValue());
            }
        }
        return new SuspicionRow(rs.getLong(1), rs.getString(2), rs.getString(3), (Long) rs.getObject(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8),
                (Integer) rs.getObject(9), rs.getInt(10), rs.getTimestamp(11).toInstant(), instant(rs.getTimestamp(12)), (Long) rs.getObject(13),
                instant(rs.getTimestamp(14)), rs.getString(15), ids);
    }

    /** 딜 · 글 · 작성자 프로필(닉네임 검색) */
    private static <T> JPAQuery<T> from(JPAQuery<T> select) {
        return select.from(DEAL).join(POST).on(POST.id.eq(DEAL.postId)).leftJoin(PROFILE).on(PROFILE.userId.eq(POST.authorId));
    }

    /** 목록 한 줄 칸 — 마지막 칸은 공개 여부(숨김 아님 · 일반 글 · 예약 삭제 전) */
    private static com.querydsl.core.types.Expression<?>[] rowColumns(Instant now) {
        return new com.querydsl.core.types.Expression<?>[] {POST.id, POST.title, POST.boardId, POST.prefixId, POST.authorId, POST.createdAt, POST.likeCount,
                publicFlag(now)};
    }

    private static NumberExpression<Integer> publicFlag(Instant now) {
        return new CaseBuilder().when(PostRepositoryQDSL.notHidden(POST, now).and(POST.groupId.isNull())
                .and(POST.deleteScheduledAt.isNull().or(POST.deleteScheduledAt.gt(now)))).then(1).otherwise(0);
    }

    private static Row toRow(Tuple t) {
        Integer pub = t.get(7, Integer.class);
        return new Row(t.get(POST.id), t.get(POST.title), t.get(POST.boardId), t.get(POST.prefixId), t.get(POST.authorId), t.get(POST.createdAt),
                t.get(POST.likeCount), pub != null && pub == 1);
    }

    /** 판매처 = 최종 호스트, 없으면 상품 호스트 */
    private static StringExpression storeHost() {
        return DEAL.finalHost.coalesce(DEAL.productHost);
    }

    /** 조건을 만족하는 줄 수({@code count(*) FILTER (WHERE ..)} 와 같다 — 빈 결과면 null) */
    private static NumberExpression<Long> countIf(BooleanExpression condition) {
        return new CaseBuilder().when(condition).then(1L).otherwise(0L).sum();
    }

    /** 기간 종료 — 마감 지남, 또는 마감 없고 등록 + 설정 일수 지남(0 = 끔). 마감도 설정도 없으면 거짓 */
    private static BooleanExpression expired(int autoExpireDays, Instant now) {
        BooleanExpression expired = DEAL.endsAt.isNotNull().and(DEAL.endsAt.loe(now));
        if (autoExpireDays > 0) {
            expired = expired.or(DEAL.endsAt.isNull().and(DEAL.createdAt.loe(now.minus(Duration.ofDays(autoExpireDays)))));
        }
        return expired;
    }

    /** 공통 조건 — 아무 글도 맞지 않으면(볼 게시판이 없음) null */
    private static BooleanBuilder where(Filter f, Instant now) {
        if (f.boardIds().isEmpty()) {
            return null;
        }
        BooleanBuilder w = new BooleanBuilder(alivePost(POST, now)).and(POST.boardId.in(f.boardIds()));
        if (f.from() != null) {
            w.and(POST.createdAt.goe(f.from()));
        }
        if (f.to() != null) {
            w.and(POST.createdAt.loe(f.to()));
        }
        if (f.source() != null) {
            w.and(DEAL.source.eq(f.source()));
        }
        if (f.q() != null) {
            w.and(Ilike.contains(POST.title, f.q()).or(Ilike.contains(DEAL.storeName, f.q())).or(Ilike.contains(PROFILE.nickname, f.q()))); // 예전 ILIKE … ESCAPE '\' 그대로
        }
        return w;
    }

    /** 상태 보기 조건(ALL 등 모르는 값이면 null — 거르지 않는다) */
    private static BooleanExpression view(String view, int autoExpireDays, Instant now) {
        BooleanExpression active = DEAL.status.eq(PostDealEntity.ACTIVE);
        return switch (view) {
            case "ACTIVE" -> active.and(expired(autoExpireDays, now).not()).and(DEAL.flaggedAt.isNull());
            case "FLAGGED" -> active.and(expired(autoExpireDays, now).not()).and(DEAL.flaggedAt.isNotNull());
            case "ENDED" -> DEAL.status.eq(PostDealEntity.ENDED);
            case "EXPIRED" -> active.and(expired(autoExpireDays, now));
            default -> null;
        };
    }

    /** 진행 중이고 상품·최종 호스트가 차단 호스트 — 차단 호스트가 없으면 아무 글도 맞지 않는다(null) */
    private static BooleanBuilder blocked(BooleanBuilder w, Collection<String> hosts, int autoExpireDays, Instant now) {
        if (hosts.isEmpty()) {
            return null;
        }
        return w.and(liveDeal(autoExpireDays, now)).and(DEAL.productHost.in(hosts).or(DEAL.finalHost.in(hosts)));
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

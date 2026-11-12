package com.community.hotdeal.repository.master;

import com.community.board.entity.QBoardEntity;
import com.community.board.entity.QBoardStaffEntity;
import com.community.hotdeal.entity.QDealEndRequestEntity;
import com.community.hotdeal.entity.QPostDealEntity;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 핫딜 집계·한도 질의 — 글 표({@code posts})와 딜 표({@code post_deals})를 함께 보는 질의, 원자적 클릭 셈. <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>「공개 글」({@link #publicPost(QPostEntity, Instant)})은 {@link PostRepositoryQDSL#alive}·{@link PostRepositoryQDSL#notHidden} + 일반 글(소모임 글 아님) — 글 쪽 규칙을 그대로 쓴다.
 * 「진행 중」({@link #liveDeal})은 딜 상태 + 기간 종료(마감 또는 등록 + 설정 일수). 관리자 {@code AdminDealQueries}·{@code DashboardGadgetStore} 의 SQL 과 같은 판정이다 — 한쪽을 고치면 같이 고친다.
 *
 * <p>Native SQL 로 남긴 것: 클릭 기록({@code deal_click_daily} — 엔티티 없음, {@code ON CONFLICT DO NOTHING}).
 */
@Repository
public class DealQueries extends IrobitRepositoryQDSL {

    private static final QPostDealEntity D = QPostDealEntity.postDealEntity;
    private static final QPostEntity P = QPostEntity.postEntity;
    private static final QBoardEntity BOARD = QBoardEntity.boardEntity;
    private static final QBoardStaffEntity STAFF = QBoardStaffEntity.boardStaffEntity;
    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QDealEndRequestEntity END = QDealEndRequestEntity.dealEndRequestEntity;
    /** DB 의 현재 시각({@link #DB_NOW} 와 같은 식) — {@code Instant} 칸과 견줄 때 */
    private static final DateTimeExpression<Instant> DB_INSTANT = Expressions.dateTimeTemplate(Instant.class, "offset_datetime");
    /** PostgreSQL 「행에 맞는 조각이 없다」(check_violation) — 클릭 판정 표의 그 날 조각이 없을 때 */
    private static final String NO_PARTITION = "23514";

    private final JdbcTemplate jdbc;

    public DealQueries(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 같은 딜 한 줄 */
    public record SameDeal(long postId, String title, long dealPrice, Long shippingFee, Instant createdAt) {
    }

    /** 종료 요청 집계 */
    public record EndCounts(int total, int soldOut, int priceChanged, int linkDead) {
    }

    /** 쇼핑몰 칩 */
    public record StoreCount(String name, long count) {
    }

    /** 공개 글(지우지 않음·공개·예약 삭제 전·숨김 아님·일반 글) */
    static BooleanExpression publicPost(QPostEntity p, Instant now) {
        return PostRepositoryQDSL.alive(p, now).and(PostRepositoryQDSL.notHidden(p, now)).and(p.groupId.isNull());
    }

    /** 진행 중(ACTIVE·FLAGGED — 종료도 기간 종료도 아님). 설정 일수가 0 이면 마감 없는 딜은 기간 종료가 없다 */
    static BooleanExpression liveDeal(QPostDealEntity d, int autoExpireDays, Instant now) {
        BooleanExpression live = d.status.eq("ACTIVE").and(d.endsAt.isNull().or(d.endsAt.gt(now)));
        if (autoExpireDays == 0) {
            return live;
        }
        return live.and(d.endsAt.isNotNull().or(d.createdAt.gt(now.minus(Duration.ofDays(autoExpireDays)))));
    }

    /** 그 회원이 {@code since} 뒤에 올린 회원 딜 수(지운 글 포함 — 지우고 다시 올려 한도를 넘지 못하게). 사업자 글은 세지 않는다(심사·결제가 대신 — biz-hotdeal 가정) */
    public int countByAuthorSince(long authorId, Instant since) {
        Long n = query.select(D.count()).from(D).where(D.authorId.eq(authorId), D.source.eq("MEMBER"), D.createdAt.goe(since)).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /** 그 회원이 {@code since} 뒤에 같은 판매처(최종 호스트, 없으면 호스트)에 올린 마지막 딜 시각 */
    public Optional<Instant> lastAtSameStore(long authorId, String storeHost, Instant since) {
        return Optional.ofNullable(query.select(D.createdAt.max()).from(D)
                .where(D.authorId.eq(authorId), D.source.eq("MEMBER"), D.finalHost.coalesce(D.productHost).eq(storeHost), D.createdAt.goe(since)).fetchOne());
    }

    /** 공개·진행 중인 같은 딜(최신 차례) — {@code excludePostId} 는 빼고 */
    public List<SameDeal> sameDeals(String key, Long excludePostId, int autoExpireDays, Instant now, int limit) {
        return query.select(Projections.constructor(SameDeal.class, D.postId, P.title, D.dealPrice, D.shippingFee, D.createdAt))
                .from(D).join(P).on(P.id.eq(D.postId))
                .where(D.productUrlKey.eq(key), D.postId.ne(excludePostId == null ? 0L : excludePostId), liveDeal(D, autoExpireDays, now), publicPost(P, now))
                .orderBy(D.postId.desc()).limit(limit).fetch();
    }

    /** 키별 공개·진행 중 글 수(목록 줄의 {@code sameDealCount} — 자기 자신이 진행 중이면 부르는 쪽이 1 을 뺀다) */
    public Map<String, Integer> liveCountsByKey(Collection<String> keys, int autoExpireDays, Instant now) {
        Map<String, Integer> out = new HashMap<>();
        if (keys.isEmpty()) {
            return out;
        }
        NumberExpression<Long> n = D.count();
        for (Tuple row : query.select(D.productUrlKey, n).from(D).join(P).on(P.id.eq(D.postId))
                .where(D.productUrlKey.in(keys), liveDeal(D, autoExpireDays, now), publicPost(P, now)).groupBy(D.productUrlKey).fetch()) {
            out.put(row.get(D.productUrlKey), Math.toIntExact(row.get(n)));
        }
        return out;
    }

    /** 글별 종료 요청 집계(요청이 없는 글은 빠진다) — 사유별 수는 {@code sum(case when reason = … then 1 else 0 end)}(예전 {@code count(*) FILTER} 와 같은 값) */
    public Map<Long, EndCounts> endRequestCounts(Collection<Long> postIds) {
        Map<Long, EndCounts> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        NumberExpression<Long> soldOut = reasonCount("SOLD_OUT");
        NumberExpression<Long> priceChanged = reasonCount("PRICE_CHANGED");
        NumberExpression<Long> linkDead = reasonCount("LINK_DEAD");
        for (Tuple t : query.select(END.postId, END.count(), soldOut, priceChanged, linkDead).from(END).where(END.postId.in(postIds)).groupBy(END.postId).fetch()) {
            out.put(t.get(END.postId), new EndCounts(Math.toIntExact(t.get(END.count())), intOf(t.get(soldOut)), intOf(t.get(priceChanged)), intOf(t.get(linkDead))));
        }
        return out;
    }

    /** 그 회원이 보낸 사유(글 ID → 사유) */
    public Map<Long, String> myEndRequests(Collection<Long> postIds, long memberId) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(END.postId, END.reason).from(END).where(END.postId.in(postIds), END.memberId.eq(memberId)).fetch()) {
            out.put(t.get(END.postId), t.get(END.reason));
        }
        return out;
    }

    private static NumberExpression<Long> reasonCount(String reason) {
        return new CaseBuilder().when(END.reason.eq(reason)).then(1L).otherwise(0L).sum();
    }

    private static int intOf(Number n) {
        return n == null ? 0 : n.intValue();
    }

    /**
     * 쇼핑몰 칩 — 공개 글, 말머리·종료 숨기기를 적용하고 쇼핑몰 거르기는 적용하지 않은 글 수(많은 차례, 같으면 이름 차례).
     *
     * @param hideEnded 종료·기간 종료 글을 뺀다
     */
    public List<StoreCount> storeChips(long boardId, Long prefixId, boolean hideEnded, int autoExpireDays, Instant now, int limit) {
        NumberExpression<Long> n = D.count();
        return query.select(Projections.constructor(StoreCount.class, D.storeName, n)).from(D).join(P).on(P.id.eq(D.postId))
                .where(P.boardId.eq(boardId), publicPost(P, now), prefixId == null ? null : P.prefixId.eq(prefixId),
                        hideEnded ? liveDeal(D, autoExpireDays, now) : null)
                .groupBy(D.storeName).orderBy(n.desc(), D.storeName.asc()).limit(limit).fetch();
    }

    /**
     * 구매 링크 클릭 — 그 날(한국 날짜) 같은 방문자·같은 글은 한 번만 센다. <b>트랜잭션 안에서 부른다</b>(셈 올리기는 JPA 대량 UPDATE).
     *
     * <p>Native SQL 인 이유(기록 한 줄): {@code deal_click_daily} 는 엔티티가 없는 표이고 「처음 한 번만」을 {@code INSERT … ON CONFLICT DO NOTHING} 한 문장으로 가린다.
     *
     * <p>판정 표는 V193 부터 한국 날짜 하루 파티션이다(조각은 DB 배치 {@code deal-click-partitions} 가 어제~모레를 미리 만든다 — R__batch_43). 그 날 조각이 없으면 PostgreSQL 이
     * 23514 로 거절하므로 <b>JDBC 세이브포인트</b>로 그 넣기만 되돌리고 조각을 한 번 만든 뒤({@code deal_ensure_click_partitions} — 잠금 아래 한 번만) 다시 넣는다.
     * 세이브포인트가 없으면 실패한 문장이 업무 트랜잭션 전체를 못 쓰게 한다. 안의 문장은 JDBC 뿐이다(세이브포인트 안에서 JPA 질의가 실패하면 롤백 전용이 된다 — repository.md).
     *
     * @return 이번에 셌다
     */
    public boolean recordClick(long postId, LocalDate day, String visitorHash, Instant now) {
        Integer inserted = jdbc.execute((ConnectionCallback<Integer>) con -> {
            Savepoint savepoint = con.getAutoCommit() ? null : con.setSavepoint();
            try {
                int n = insertClick(con, postId, day, visitorHash, now);
                if (savepoint != null) {
                    con.releaseSavepoint(savepoint);
                }
                return n;
            } catch (SQLException e) {
                if (savepoint != null) {
                    con.rollback(savepoint);
                }
                if (!NO_PARTITION.equals(e.getSQLState())) {
                    throw e;
                }
                try (PreparedStatement ps = con.prepareStatement("SELECT deal_ensure_click_partitions(?)")) {
                    ps.setDate(1, java.sql.Date.valueOf(day));
                    ps.execute();
                }
                return insertClick(con, postId, day, visitorHash, now); // 조각이 없어 거절됐다 — 한 번 만들고 다시
            }
        });
        if (inserted == null || inserted == 0) {
            return false;
        }
        query.update(D).set(D.clickCount, D.clickCount.add(1)).where(D.postId.eq(postId)).execute();
        return true;
    }

    private static int insertClick(Connection con, long postId, LocalDate day, String visitorHash, Instant now) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO deal_click_daily (post_id, day, visitor_hash, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setLong(1, postId);
            ps.setDate(2, java.sql.Date.valueOf(day));
            ps.setString(3, visitorHash);
            ps.setTimestamp(4, Timestamp.from(now));
            return ps.executeUpdate();
        }
    }

    // ── 2단계: 가젯 · 인기글 · 배치 · 탈퇴 ─────────────────────────────────────

    /** 핫딜 가젯 정렬 */
    public enum GadgetSort {
        /** 최신(글 ID 큰 차례) */
        LATEST,
        /** 인기(추천 많은 차례, 같으면 최신) */
        POPULAR,
        /** 마감 임박(기간 종료 시각 — 마감 또는 등록 + 설정 일수 — 빠른 차례, 그 시각이 없는 글은 뒤, 같으면 최신) */
        ENDING_SOON
    }

    /**
     * 핫딜 가젯의 글(규칙 8) — 공개 글 · 저장 상태 ACTIVE · 의심 아님 · 기간 종료 아님. 게시판은 부르는 쪽이 고른 핫딜 게시판들.
     *
     * @return 글 ID(정렬 차례)
     */
    public List<Long> gadgetDealIds(Collection<Long> boardIds, GadgetSort sort, int autoExpireDays, Instant now, int limit) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        List<OrderSpecifier<?>> order = new ArrayList<>();
        switch (sort) {
            case POPULAR -> order.add(P.likeCount.desc());
            case ENDING_SOON -> order.add(expiresAt(autoExpireDays).asc().nullsLast());
            default -> {
            }
        }
        order.add(D.postId.desc());
        return query.select(D.postId).from(D).join(P).on(P.id.eq(D.postId))
                .where(D.boardId.in(boardIds), liveDeal(D, autoExpireDays, now), D.flaggedAt.isNull(), publicPost(P, now))
                .orderBy(order.toArray(OrderSpecifier[]::new)).limit(limit).fetch();
    }

    /** 기간 종료 시각 — 마감이 있으면 마감, 없으면 등록 + 설정 일수(0 이면 없음 = null) */
    private static DateTimeExpression<Instant> expiresAt(int autoExpireDays) {
        if (autoExpireDays <= 0) {
            return D.endsAt;
        }
        // 일수는 설정의 정수라 식에 그대로 넣는다(HQL 기간 덧셈 — PostgreSQL 에서 created_at + 일수 * interval '1 day')
        return new CaseBuilder().when(D.endsAt.isNotNull()).then(D.endsAt)
                .otherwise(Expressions.dateTimeTemplate(Instant.class, "{0} + " + autoExpireDays + " day", D.createdAt));
    }

    /**
     * 인기글에 올리지 않는 핫딜 글(규칙 8·10) — 종료 · 기간 종료 · 사업자 글. 의심은 올린다. 배치 {@code popular_rank_candidates}(R__batch_24)와 같은 판정 — 한쪽을 고치면 같이 고친다.
     */
    public Set<Long> unrankable(Collection<Long> postIds, int autoExpireDays, Instant now) {
        Set<Long> out = new HashSet<>();
        if (postIds.isEmpty()) {
            return out;
        }
        out.addAll(query.select(D.postId).from(D).where(D.postId.in(postIds), D.source.eq("BUSINESS").or(liveDeal(D, autoExpireDays, now).not())).fetch());
        return out;
    }

    /** 자동 종료 후보(배치) — 진행 중 · 의심 · {@code flaggedBefore} 이전에 의심이 됨 · 회원 글만(사업자 글은 운영자 확인 — biz-hotdeal 결정 11). 글 ID 키셋 */
    public List<Long> autoEndCandidates(Instant flaggedBefore, long afterPostId, int limit) {
        return query.select(D.postId).from(D)
                .where(D.status.eq("ACTIVE"), D.source.ne("BUSINESS"), D.flaggedAt.isNotNull(), D.flaggedAt.loe(flaggedBefore), D.postId.gt(afterPostId))
                .orderBy(D.postId.asc()).limit(limit).fetch();
    }

    /** 마감 없는 딜의 기간 종료 알림 후보(배치) — 진행 중 · 마감 없음 · 아직 안 알림 · {@code createdBefore} 이전 등록. 글 ID 키셋 */
    public List<Long> expiryCandidates(Instant createdBefore, long afterPostId, int limit) {
        return query.select(D.postId).from(D)
                .where(D.status.eq("ACTIVE"), D.endsAt.isNull(), D.expiryNotifiedAt.isNull(), D.createdAt.loe(createdBefore), D.postId.gt(afterPostId))
                .orderBy(D.postId.asc()).limit(limit).fetch();
    }

    /** 공개 글인가(지우지 않음·공개·예약 삭제 전·숨김 아님·일반 글) */
    public boolean publicPost(long postId, Instant now) {
        return query.selectOne().from(P).where(P.id.eq(postId), publicPost(P, now)).fetchFirst() != null;
    }

    /** 알릴 때의 글 제목(글 표는 파티션 표라 연관이 없다 — 없으면 null) */
    public String titleOf(long postId) {
        return query.select(P.title).from(P).where(P.id.eq(postId)).fetchFirst();
    }

    /**
     * 운영진 종료·재개의 대상 글이 있는 게시판 — 지우지 않음 · 공개 · 예약 삭제 전(DB 시각) · 지금 핫딜 게시판({@code boards.kind = DEAL}) · 딜 줄이 있음. 아니면 비어 있다.
     * 숨긴 글·소모임 글도 대상이다(보이기 조건이 아니라 「살아 있는 글」).
     */
    public Optional<Long> moderationBoardId(long postId) {
        return Optional.ofNullable(query.select(P.boardId).from(P).join(D).on(D.postId.eq(P.id)).join(BOARD).on(BOARD.id.eq(P.boardId))
                .where(P.id.eq(postId), BOARD.kind.eq("DEAL"), P.deletedAt.isNull(), P.status.eq(PostEntity.PUBLISHED),
                        P.deleteScheduledAt.isNull().or(P.deleteScheduledAt.gt(DB_INSTANT)))
                .fetchFirst());
    }

    /** 그 게시판에서 이 회원이 맡은 자리(MANAGER · STAFF) — 매니저·스텝이 아니거나 활성 회원({@code users.status = ACTIVE})이 아니면 null */
    public String boardRole(long boardId, long memberId) {
        return query.select(STAFF.role).from(STAFF).join(USER).on(USER.id.eq(STAFF.userId))
                .where(STAFF.boardId.eq(boardId), STAFF.userId.eq(memberId), USER.status.eq(UserStatus.ACTIVE)).fetchFirst();
    }

    /** 그 회원이 쓴 저장 상태 진행 중(ACTIVE) 딜(탈퇴 — 규칙 6) */
    public List<Long> activeByAuthor(long authorId) {
        return query.select(D.postId).from(D).where(D.authorId.eq(authorId), D.status.eq("ACTIVE")).orderBy(D.postId.asc()).fetch();
    }
}

package com.community.point.repository.master;

import com.community.point.entity.PointLotKind;
import com.community.point.entity.QPointBalanceEntity;
import com.community.point.entity.QPointLotEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 포인트 배치(소멸 {@code point-expire} · 소멸 예정 알림 {@code point-expiry-notice})가 대상을 고르는 읽기와 알림 표시 — {@code com.community.point.batch} 만 부른다.
 * 묶음의 남은 양은 여기서 바꾸지 않는다(소멸은 {@code PointService#expireLot} 한 길). 알림 표시({@code expiry_noticed_at})만 여기서 쓴다 — 남은 양·잔액과 무관한 칸이라 잔액 행 잠금이 필요 없고
 * ({@code point_lots_identity_fixed} 트리거가 허용하는 칸), 조건부 UPDATE 라 같은 묶음을 두 번 표시하지 않는다.
 *
 * <p>둘 다 V85 의 부분 색인 {@code ix_point_lots_expiry (expires_at) WHERE kind = 'FREE' AND remaining_amount > 0} 을 탄다. {@code userId} 가 있으면 그 회원만(배치 파라미터 — 특정 회원만 돌리기).
 * QueryDSL 이 기본이다 — 키셋 {@code (expires_at, id) > (?, ?)} 은 같은 뜻의 OR 식으로, {@code = any(?)} 는 {@code in} 으로 쓴다. 트랜잭션은 배치 실행기가 연다.
 * Native SQL 로 남긴 것과 이유: 알림 표시 {@code UPDATE … RETURNING} — 표시한 묶음과 알릴 묶음이 같은 문장의 결과여야 한다(나누면 그 사이 다른 실행이 표시한 묶음이 섞인다).
 */
@Repository
public class PointLotBatchQueries extends IrobitRepositoryQDSL {

    /** 소멸 대상 묶음 하나 — 정렬 키 (소멸일, ID) */
    public record DueLot(long id, long userId, Instant expiresAt) {
    }

    /** 방금 알림 표시한 묶음 하나 */
    public record NoticedLot(long id, long remaining, Instant expiresAt) {
    }

    /** 알림 기간 안 무상 묶음의 요약 — 가장 가까운 소멸 시각과 기간 안 남은 양 합 */
    public record ExpiringSummary(Instant firstExpiresAt, long amount) {
    }

    private static final QPointLotEntity L = QPointLotEntity.pointLotEntity;
    private static final QPointBalanceEntity B = QPointBalanceEntity.pointBalanceEntity;

    private static final String NOTICE_TARGET = "kind = 'FREE' and remaining_amount > 0 and expiry_noticed_at is null and expires_at > ?3 and expires_at <= ?4 ";

    private final EntityManager entityManager;

    public PointLotBatchQueries(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    // ── 소멸 ────────────────────────────────────────────────────────────────

    /** 소멸일이 지났는데 남은 양이 있는 무상 묶음 */
    private static BooleanExpression due(Instant now) {
        return L.kind.eq(PointLotKind.FREE).and(L.remainingAmount.gt(0L)).and(L.expiresAt.loe(now));
    }

    /**
     * 소멸일({@code expires_at ≤ now})이 지났는데 남은 양이 있는 무상 묶음 — 「아직 처리 안 한 것 전부」(15-3 규칙 ②). (소멸일, ID) 오름차순, {@code after} 다음부터 {@code size} 개.
     *
     * @param after 마지막으로 끝낸 묶음(없으면 null — 처음부터)
     */
    public List<DueLot> dueLots(Instant now, DueLot after, Long userId, int size) {
        BooleanBuilder where = new BooleanBuilder(due(now));
        if (userId != null) {
            where.and(L.userId.eq(userId));
        }
        if (after != null) {
            // (expires_at, id) > (?, ?) — 대상의 소멸일은 늘 있다(expires_at ≤ now)
            where.and(L.expiresAt.gt(after.expiresAt()).or(L.expiresAt.eq(after.expiresAt()).and(L.id.gt(after.id()))));
        }
        return query.select(L.id, L.userId, L.expiresAt).from(L).where(where).orderBy(L.expiresAt.asc(), L.id.asc()).limit(size).fetch().stream()
                .map(PointLotBatchQueries::dueOf).toList();
    }

    /** 「실패 건만 다시」 — 그 묶음들 중 아직 소멸 대상인 것(이미 소멸했으면 빠진다) */
    public List<DueLot> dueLotsByIds(Instant now, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.select(L.id, L.userId, L.expiresAt).from(L).where(due(now), L.id.in(ids)).orderBy(L.expiresAt.asc(), L.id.asc()).fetch().stream()
                .map(PointLotBatchQueries::dueOf).toList();
    }

    // ── 소멸 예정 알림 ──────────────────────────────────────────────────────

    /** 알림 기간({@code from < expires_at ≤ until}) 안에 아직 알리지 않은 무상 묶음 + 쉬는 회원 빼기 */
    private static BooleanBuilder noticeTarget(Instant from, Instant until, Instant restSince) {
        BooleanBuilder where = new BooleanBuilder(L.kind.eq(PointLotKind.FREE)).and(L.remainingAmount.gt(0L)).and(L.expiryNoticedAt.isNull())
                .and(L.expiresAt.gt(from)).and(L.expiresAt.loe(until));
        if (restSince != null) {
            // 쉬는 회원 빼기 — 마지막 알림이 restSince 이후인 회원(회원당 최소 간격)
            where.and(JPAExpressions.selectOne().from(B).where(B.userId.eq(L.userId), B.expiryNoticeSentAt.goe(restSince)).notExists());
        }
        return where;
    }

    /**
     * 알림 기간({@code from < expires_at ≤ until}) 안에 아직 알리지 않은 무상 묶음이 있는 회원 — 회원 ID 오름차순, {@code afterUserId} 다음부터 {@code size} 명.
     * {@code restSince} 가 있으면 그 뒤에 이미 알림을 받은 회원은 뺀다(그 회원의 묶음은 표시하지 않고 남겨 다음에 알린다).
     */
    public List<Long> noticeMembers(Instant from, Instant until, Instant restSince, Long afterUserId, Long userId, int size) {
        BooleanBuilder where = noticeTarget(from, until, restSince);
        if (userId != null) {
            where.and(L.userId.eq(userId));
        }
        if (afterUserId != null) {
            where.and(L.userId.gt(afterUserId));
        }
        return query.selectDistinct(L.userId).from(L).where(where).orderBy(L.userId.asc()).limit(size).fetch();
    }

    /** 「실패 건만 다시」 — 그 회원들 중 아직 알릴 묶음이 있는 회원 */
    public List<Long> noticeMembersByIds(Instant from, Instant until, Instant restSince, List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return query.selectDistinct(L.userId).from(L).where(noticeTarget(from, until, restSince).and(L.userId.in(userIds))).orderBy(L.userId.asc()).fetch();
    }

    /**
     * 그 회원의 알림 기간 안 아직 알리지 않은 무상 묶음에 알린 시각을 적는다 — <b>조건부</b>({@code expiry_noticed_at IS NULL})라 같은 묶음을 두 번 표시하지 않는다. 방금 표시한 묶음.
     *
     * <p>Native SQL 인 이유: {@code UPDATE … RETURNING} — 표시한 묶음과 돌려줄 묶음이 같은 문장의 결과여야 한다(클래스 설명).
     */
    public List<NoticedLot> markNoticed(long userId, Instant from, Instant until, Instant noticedAt) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("update point_lots set expiry_noticed_at = ?1 where user_id = ?2 and " + NOTICE_TARGET
                        + "returning id, remaining_amount, expires_at")
                .setParameter(1, ts(noticedAt)).setParameter(2, userId).setParameter(3, ts(from)).setParameter(4, ts(until)).getResultList();
        return rows.stream().map(r -> new NoticedLot(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), instantOf(r[2]))).toList();
    }

    /**
     * 그 회원의 마지막 알림 시각을 적는다 — <b>조건부</b>: {@code restSince} 이후에 이미 알렸으면 적지 않고 false(같은 때 도는 다른 실행과 겹쳐도 한 번만 알린다).
     * 잔액 행의 알림 칸만 바꾼다(잔액·묶음과 무관 — 잔액 행 잠금은 이 문장 동안만).
     */
    public boolean claimNotice(long userId, Instant sentAt, Instant restSince) {
        if (restSince == null) {
            return query.update(B).set(B.expiryNoticeSentAt, sentAt).where(B.userId.eq(userId)).execute() == 1;
        }
        return query.update(B).set(B.expiryNoticeSentAt, sentAt)
                .where(B.userId.eq(userId), B.expiryNoticeSentAt.isNull().or(B.expiryNoticeSentAt.lt(restSince))).execute() == 1;
    }

    /** 그 회원의 알림 기간 안 무상 묶음 전부(이미 알린 것 포함)의 가장 가까운 소멸 시각과 남은 양 합 — 없으면 빈 값 */
    public Optional<ExpiringSummary> expiringSummary(long userId, Instant from, Instant until) {
        Tuple t = query.select(L.expiresAt.min(), L.remainingAmount.sum().coalesce(0L)).from(L)
                .where(L.userId.eq(userId), L.kind.eq(PointLotKind.FREE), L.remainingAmount.gt(0L), L.expiresAt.gt(from), L.expiresAt.loe(until)).fetchOne();
        Instant first = t == null ? null : t.get(0, Instant.class);
        if (first == null) {
            return Optional.empty();
        }
        Long amount = t.get(1, Long.class);
        return Optional.of(new ExpiringSummary(first, amount == null ? 0 : amount));
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static DueLot dueOf(Tuple t) {
        return new DueLot(t.get(L.id), t.get(L.userId), t.get(L.expiresAt));
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    /** native 결과의 {@code timestamptz} 한 칸 */
    private static Instant instantOf(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("모르는 시각 값: " + value);
    }
}

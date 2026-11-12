package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.QGroupBuyCategoryEntity;
import com.community.groupbuy.entity.QGroupBuyDelayNoticeEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyRequestEntity;
import com.community.groupbuy.entity.QGroupBuySupplyFailureEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 회원 공구 화면({@code GroupBuyMemberService} · {@code MyGroupBuyOrderService})이 읽는 값 — 서비스 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다(읽기만).
 */
@Repository
public class GroupBuyMemberQueries extends IrobitRepositoryQDSL {

    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuyCategoryEntity C = QGroupBuyCategoryEntity.groupBuyCategoryEntity;
    private static final QGroupBuyCategoryEntity PC = new QGroupBuyCategoryEntity("parentCategory");
    private static final QGroupBuyDelayNoticeEntity D = QGroupBuyDelayNoticeEntity.groupBuyDelayNoticeEntity;
    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyRequestEntity RQ = QGroupBuyRequestEntity.groupBuyRequestEntity;
    private static final QGroupBuySupplyFailureEntity F = QGroupBuySupplyFailureEntity.groupBuySupplyFailureEntity;

    /** 회원 목록 탭 */
    public enum Phase {
        /** 공개된 DRAFT — 시작 이른 순 */
        UPCOMING,
        /** 모집 중·집계 중 — 마감(연장 중이면 연장 끝) 이른 순 */
        ONGOING,
        /** 그 밖 — 마감 집계 늦은 순(없으면 뒤), 고친 늦은 순 */
        ENDED
    }

    /** 홈 공구 가젯 차례 — 마감 이른 순 · 새로 시작한 순 · 참여 많은 순(같으면 마감 이른 순) */
    public enum RecruitingSort {
        DEADLINE, NEWEST, POPULAR
    }

    /** 발송 지연 안내 한 줄 */
    public record DelayNoticeRow(LocalDate previousPlannedAt, LocalDate newPlannedAt, String reason, Instant createdAt) {
    }

    /** 공급 불가 기록의 단품·사유 */
    public record SupplyFailureRow(long skuId, String reason) {
    }

    public GroupBuyMemberQueries(JPAQueryFactory query) {
        super(query);
    }

    // ── 공구 목록 ───────────────────────────────────────────────────────────

    /** 지금 마감 시각(연장 중이면 연장 끝) */
    private static DateTimeExpression<Instant> deadline() {
        return Expressions.dateTimeTemplate(Instant.class, "coalesce({0}, {1})", G.extendedUntil, G.endAt);
    }

    /** 회원에게 보이는 공구(공개됨 · 숨긴 카테고리와 그 아래 제외) */
    private <T> JPAQuery<T> visible(JPAQuery<T> q) {
        return q.from(G).join(P).on(P.id.eq(G.productId)).join(C).on(C.id.eq(P.categoryId)).leftJoin(PC).on(PC.id.eq(C.parentId))
                .where(G.publishedAt.isNotNull(), C.hidden.isFalse(), PC.hidden.coalesce(false).isFalse());
    }

    private static BooleanExpression phase(Phase phase) {
        return switch (phase) {
            case UPCOMING -> G.status.eq(GroupBuyStatus.DRAFT);
            case ONGOING -> G.status.in(GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING);
            case ENDED -> G.status.notIn(GroupBuyStatus.DRAFT, GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING);
        };
    }

    private static BooleanExpression category(Long categoryId) {
        return categoryId == null ? null : C.id.eq(categoryId).or(C.parentId.eq(categoryId));
    }

    public long count(Phase phase, Long categoryId) {
        Long n = visible(query.select(G.count())).where(phase(phase), category(categoryId)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 탭 목록 한 쪽의 공구 ID */
    public List<Long> pageIds(Phase phase, Long categoryId, long offset, int limit) {
        OrderSpecifier<?>[] order = switch (phase) {
            case UPCOMING -> new OrderSpecifier<?>[] {G.startAt.asc(), G.id.asc()};
            case ONGOING -> new OrderSpecifier<?>[] {deadline().asc(), G.id.asc()};
            case ENDED -> new OrderSpecifier<?>[] {G.closingAt.desc().nullsLast(), G.updatedAt.desc(), G.id.desc()};
        };
        return visible(query.select(G.id)).where(phase(phase), category(categoryId)).orderBy(order).offset(offset).limit(limit).fetch();
    }

    /** 모집 중(RECRUITING)인 공개 공구 {@code count} 개 */
    public List<Long> recruitingIds(RecruitingSort sort, int count) {
        OrderSpecifier<?>[] order = switch (sort) {
            case DEADLINE -> new OrderSpecifier<?>[] {deadline().asc(), G.id.asc()};
            case NEWEST -> new OrderSpecifier<?>[] {G.startAt.desc(), G.id.desc()};
            case POPULAR -> new OrderSpecifier<?>[] {G.currentQty.desc(), deadline().asc(), G.id.asc()};
        };
        return visible(query.select(G.id)).where(G.status.eq(GroupBuyStatus.RECRUITING)).orderBy(order).limit(count).fetch();
    }

    /** 가장 최근 발송 지연 안내 */
    public Optional<DelayNoticeRow> latestDelayNotice(long groupBuyId) {
        Tuple t = query.select(D.previousPlannedAt, D.newPlannedAt, D.reason, D.createdAt).from(D).where(D.groupBuyId.eq(groupBuyId)).orderBy(D.createdAt.desc()).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new DelayNoticeRow(t.get(D.previousPlannedAt), t.get(D.newPlannedAt), t.get(D.reason), t.get(D.createdAt)));
    }

    // ── 내 주문 ─────────────────────────────────────────────────────────────

    public long myOrderCount(long userId) {
        Long n = query.select(PA.count()).from(PA).where(PA.userId.eq(userId)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 내 주문 한 쪽의 ID — 새 주문 먼저 */
    public List<Long> myOrderIds(long userId, long offset, int limit) {
        return query.select(PA.id).from(PA).where(PA.userId.eq(userId)).orderBy(PA.createdAt.desc(), PA.id.desc()).offset(offset).limit(limit).fetch();
    }

    /** 그 회원의 주문 번호 → 주문 ID */
    public Optional<Long> myOrderId(String orderNo, long userId) {
        return Optional.ofNullable(query.select(PA.id).from(PA).where(PA.orderNo.eq(orderNo), PA.userId.eq(userId)).fetchFirst());
    }

    public Optional<SupplyFailureRow> supplyFailure(long id) {
        Tuple t = query.select(F.skuId, F.reason).from(F).where(F.id.eq(id)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new SupplyFailureRow(t.get(F.skuId), t.get(F.reason)));
    }

    /** 요청을 처리한 시각(끝낸 시각, 없으면 결정한 시각) */
    public Instant requestHandledAt(long requestId) {
        Tuple t = query.select(RQ.completedAt, RQ.decidedAt).from(RQ).where(RQ.id.eq(requestId)).fetchFirst();
        return t == null ? null : t.get(RQ.completedAt) != null ? t.get(RQ.completedAt) : t.get(RQ.decidedAt);
    }

    /** 회수 송장 번호(없으면 null) */
    public String pickupTrackingNo(long requestId) {
        return query.select(RQ.pickupTrackingNo).from(RQ).where(RQ.id.eq(requestId)).fetchFirst();
    }
}

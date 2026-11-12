package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QGroupBuyCategoryEntity;
import com.community.groupbuy.entity.QGroupBuyDelayNoticeEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationItemEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyProductSkuEntity;
import com.community.groupbuy.entity.QGroupBuyQtyMovementEntity;
import com.community.groupbuy.entity.QGroupBuyRefundEntity;
import com.community.groupbuy.entity.QGroupBuyRevisionEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.QGroupBuySupplyChoiceEntity;
import com.community.groupbuy.entity.QGroupBuySupplyFailureEntity;
import com.community.groupbuy.entity.QtyMovementKind;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.entity.SkuKind;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 관리자 공구·수량 화면({@code GroupBuyAdminFacade})이 읽는 값 — Facade 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다(읽기만, 부르는 쪽 트랜잭션에 참여한다).
 * 예전 {@code string_agg}(선점 줄 요약)는 줄을 따로 읽어 자바가 잇는다({@link #holdLines}).
 */
@Repository
public class GroupBuyAdminQueries extends IrobitRepositoryQDSL {

    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuyCategoryEntity C = QGroupBuyCategoryEntity.groupBuyCategoryEntity;
    private static final QGroupBuyCategoryEntity PC = new QGroupBuyCategoryEntity("parentCategory");
    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyParticipationItemEntity I = QGroupBuyParticipationItemEntity.groupBuyParticipationItemEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;
    private static final QGroupBuyProductSkuEntity PS = QGroupBuyProductSkuEntity.groupBuyProductSkuEntity;
    private static final QGroupBuyRevisionEntity REV = QGroupBuyRevisionEntity.groupBuyRevisionEntity;
    private static final QGroupBuyQtyMovementEntity M = QGroupBuyQtyMovementEntity.groupBuyQtyMovementEntity;
    private static final QGroupBuyDelayNoticeEntity D = QGroupBuyDelayNoticeEntity.groupBuyDelayNoticeEntity;
    private static final QGroupBuySupplyFailureEntity F = QGroupBuySupplyFailureEntity.groupBuySupplyFailureEntity;
    private static final QGroupBuySupplyChoiceEntity CH = QGroupBuySupplyChoiceEntity.groupBuySupplyChoiceEntity;
    private static final QGroupBuyRefundEntity R = QGroupBuyRefundEntity.groupBuyRefundEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;

    /** 목록 거르기 — {@code like} 제목·상품 이름 ILIKE 말, {@code exact} 공구 번호 글자(없으면 둘 다 null), {@code status} 상태(없으면 null) */
    public record ListFilter(String like, String exact, GroupBuyStatus status) {
    }

    public record RoundRow(long id, int roundNo, GroupBuyStatus status, String title) {
    }

    public record RevisionRow(int revisionNo, String changeKind, String title, String summary, String description, String imagesJson, int maxQty, int addedQty,
                              String skusJson, String noticesJson, String operatorName, Instant createdAt, LocalDate shipPlannedAt) {
    }

    /** 공급 불가 미리 보기의 주문 — 남은 현금(결제액 − 환불 − 보상) */
    public record SupplyOrder(long participationId, long remaining) {
    }

    /** 주문 줄 — {@code held} 남은 수량 */
    public record HeldLine(long skuId, SkuKind kind, int held, long unitPrice) {
    }

    public record HoldRow(long participationId, String orderNo, long userId, String nickname, boolean gone, Instant reservedUntil) {
    }

    /** 선점 줄 요약 — 「이름 × 수량」을 단품 종류(MAIN 먼저)·차례로 이은 것과 수량 합 */
    public record HoldLines(String summary, int qty) {
    }

    public record MovementRow(long id, QtyMovementKind kind, String skuName, SkuKind skuKind, int qty, Integer currentAfter, Integer reservedAfter, Integer maxAfter,
                              Integer skuCurrentAfter, Integer skuReservedAfter, Integer skuMaxAfter, String orderNo, String operatorName, String reason, Instant createdAt,
                              Long actorId) {
    }

    public record DelayNoticeRow(long id, LocalDate previousPlannedAt, LocalDate newPlannedAt, String reason, String operatorName, Instant createdAt, int notifiedOrders) {
    }

    public record SupplyFailureRow(long id, long skuId, String reason, Instant choiceUntil, String operatorName, Instant createdAt, int refundedLines, long refundAmount,
                                   int choiceOrders, int pendingChoices) {
    }

    public GroupBuyAdminQueries(JPAQueryFactory query) {
        super(query);
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    private static BooleanExpression search(ListFilter f) {
        if (f.like() == null) {
            return null;
        }
        return GroupBuyRowNames.ilike(G.title, f.like()).or(GroupBuyRowNames.ilike(P.name, f.like())).or(G.id.stringValue().eq(f.exact()));
    }

    /** 찾기 말만 건 상태별 수(상태 거르기 전 — 탭 수) */
    public Map<GroupBuyStatus, Long> statusCounts(ListFilter f) {
        Map<GroupBuyStatus, Long> out = new LinkedHashMap<>();
        query.select(G.status, G.count()).from(G).join(P).on(P.id.eq(G.productId)).where(search(f)).groupBy(G.status).fetch()
                .forEach(t -> out.put(t.get(0, GroupBuyStatus.class), n(t.get(1, Long.class))));
        return out;
    }

    private static BooleanExpression listWhere(ListFilter f) {
        BooleanExpression s = search(f);
        BooleanExpression st = f.status() == null ? null : G.status.eq(f.status());
        return s == null ? st : st == null ? s : s.and(st);
    }

    public long count(ListFilter f) {
        return n(query.select(G.count()).from(G).join(P).on(P.id.eq(G.productId)).where(listWhere(f)).fetchOne());
    }

    /** 한 쪽의 공구 ID — 최근에 고친 것 먼저 */
    public List<Long> pageIds(ListFilter f, long offset, int limit) {
        return query.select(G.id).from(G).join(P).on(P.id.eq(G.productId)).where(listWhere(f)).orderBy(G.updatedAt.desc(), G.id.desc()).offset(offset).limit(limit).fetch();
    }

    /** 공구의 주문 수(선점 만료 제외) */
    public int orderCount(long groupBuyId) {
        return (int) n(query.select(PA.count()).from(PA).where(PA.groupBuyId.eq(groupBuyId), PA.status.ne(ParticipationStatus.EXPIRED)).fetchOne());
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    /** 같은 묶음의 차수들 — 차수 차례 */
    public List<RoundRow> rounds(long roundRootId) {
        return query.select(G.id, G.roundNo, G.status, G.title, P.name).from(G).join(P).on(P.id.eq(G.productId)).where(G.roundRootId.eq(roundRootId)).orderBy(G.roundNo.asc())
                .fetch().stream().map(t -> new RoundRow(t.get(G.id), n(t.get(G.roundNo)), t.get(G.status), GroupBuyRowNames.title(t.get(G.title), t.get(P.name)))).toList();
    }

    /** 발송 전 주문 수(JOINED · CONFIRMED · PREPARING) */
    public int preShipCount(long groupBuyId) {
        return (int) n(query.select(PA.count()).from(PA).where(PA.groupBuyId.eq(groupBuyId), PA.status.in(ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED,
                ParticipationStatus.PREPARING)).fetchOne());
    }

    /** 운영자 이름(없으면 null) */
    public String operatorName(long operatorId) {
        return query.select(O.operatorName).from(O).where(O.id.eq(operatorId)).fetchFirst();
    }

    /** 카테고리 경로 — 「위 › 아래」(위 단이면 이름만, 없으면 빈 글자) */
    public String categoryPath(long categoryId) {
        Tuple t = query.select(C.name, PC.name).from(C).leftJoin(PC).on(PC.id.eq(C.parentId)).where(C.id.eq(categoryId)).fetchFirst();
        if (t == null) {
            return "";
        }
        String parent = t.get(PC.name);
        String name = t.get(C.name);
        return parent == null ? name : parent + " › " + name;
    }

    /** 공구의 추가 구성상품이 속한 상품들(중복 없이) */
    public List<Long> addonProductIds(long groupBuyId) {
        return query.select(PS.productId).distinct().from(S).join(PS).on(PS.id.eq(S.productSkuId)).where(S.groupBuyId.eq(groupBuyId), S.kind.eq(SkuKind.ADDON)).fetch();
    }

    public long revisionCount(long groupBuyId) {
        return n(query.select(REV.count()).from(REV).where(REV.groupBuyId.eq(groupBuyId)).fetchOne());
    }

    /** 수정 이력 한 쪽 — 판 번호 큰 것 먼저. 발송 예정일은 지금 공구 값 */
    public List<RevisionRow> revisions(long groupBuyId, long offset, int limit) {
        QGroupBuyEntity now = new QGroupBuyEntity("currentGroupBuy");
        Expression<LocalDate> shipPlannedAt = JPAExpressions.select(now.shipPlannedAt).from(now).where(now.id.eq(REV.groupBuyId));
        return query.select(REV.revisionNo, REV.changeKind, REV.title, REV.summary, REV.description, REV.images, REV.maxQty, REV.addedQty, REV.skus, REV.notices,
                        O.operatorName, REV.createdAt, shipPlannedAt)
                .from(REV).leftJoin(O).on(O.id.eq(REV.editedBy)).where(REV.groupBuyId.eq(groupBuyId)).orderBy(REV.revisionNo.desc(), REV.createdAt.desc())
                .offset(offset).limit(limit).fetch().stream()
                .map(t -> new RevisionRow(n(t.get(REV.revisionNo)), t.get(REV.changeKind), t.get(REV.title), t.get(REV.summary), t.get(REV.description), t.get(REV.images),
                        n(t.get(REV.maxQty)), n(t.get(REV.addedQty)), t.get(REV.skus), t.get(REV.notices), t.get(O.operatorName), t.get(REV.createdAt),
                        t.get(12, LocalDate.class))).toList();
    }

    private static NumberExpression<Integer> held() {
        return I.qty.subtract(I.canceledQty).subtract(I.returnedQty);
    }

    /** 공급 불가 미리 보기 — 그 단품이 남은 발송 전 주문 */
    public List<SupplyOrder> supplyFailureOrders(long groupBuyId, long skuId) {
        QGroupBuyParticipationItemEntity i = new QGroupBuyParticipationItemEntity("heldItem");
        return query.select(PA.id, PA.cashAmount.subtract(PA.refundedAmount).subtract(PA.compensationAmount)).from(PA)
                .where(PA.groupBuyId.eq(groupBuyId), PA.status.in(ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING),
                        JPAExpressions.selectOne().from(i).where(i.participationId.eq(PA.id), i.skuId.eq(skuId), i.qty.subtract(i.canceledQty).subtract(i.returnedQty).gt(0))
                                .exists())
                .fetch().stream().map(t -> new SupplyOrder(t.get(0, Long.class), n(t.get(1, Long.class)))).toList();
    }

    /** 주문의 줄(단품 종류 · 남은 수량 · 단가) */
    public List<HeldLine> heldLines(long participationId) {
        return query.select(I.skuId, S.kind, held(), I.unitPrice).from(I).join(S).on(S.id.eq(I.skuId)).where(I.participationId.eq(participationId)).fetch().stream()
                .map(t -> new HeldLine(t.get(I.skuId), t.get(S.kind), n(t.get(2, Integer.class)), n(t.get(I.unitPrice)))).toList();
    }

    // ── 수량 ────────────────────────────────────────────────────────────────

    /** 단품마다 반품·재입고 수량 합 — (반품, 재입고) */
    public Map<Long, int[]> returnedBySku(long groupBuyId) {
        Map<Long, int[]> out = new LinkedHashMap<>();
        query.select(I.skuId, sumLong(I.returnedQty), sumLong(I.restockedQty)).from(I).where(I.groupBuyId.eq(groupBuyId)).groupBy(I.skuId).fetch()
                .forEach(t -> out.put(t.get(0, Long.class), new int[] {(int) n(t.get(1, Long.class)), (int) n(t.get(2, Long.class))}));
        return out;
    }

    /** 살아 있는 선점(결제 대기 · 선점 시각 안) — 선점이 먼저 끝나는 것 먼저, 같으면 id 순(차례가 흔들리지 않게) */
    public List<HoldRow> holds(long groupBuyId, Instant now) {
        return query.select(PA.id, PA.orderNo, PA.userId, PR.nickname, U.id, U.status, U.deletedAt, PA.reservedUntil).from(PA).leftJoin(U).on(U.id.eq(PA.userId))
                .leftJoin(PR).on(PR.userId.eq(PA.userId)).where(PA.groupBuyId.eq(groupBuyId), PA.status.eq(ParticipationStatus.PENDING_PAYMENT), PA.reservedUntil.gt(now))
                .orderBy(PA.reservedUntil.asc(), PA.id.asc()).fetch().stream()
                .map(t -> new HoldRow(t.get(PA.id), t.get(PA.orderNo), t.get(PA.userId), t.get(PR.nickname), GroupBuyRowNames.gone(t.get(U.id), t.get(U.status),
                        t.get(U.deletedAt)), t.get(PA.reservedUntil))).toList();
    }

    /** 주문마다 선점 줄 요약 — 예전 {@code string_agg(s.name || ' × ' || i.qty, ', ' ORDER BY s.kind DESC, s.sort_order)} · {@code sum(i.qty)} */
    public Map<Long, HoldLines> holdLines(Collection<Long> participationIds) {
        Map<Long, List<String>> parts = new LinkedHashMap<>();
        Map<Long, Integer> qty = new LinkedHashMap<>();
        if (participationIds.isEmpty()) {
            return Map.of();
        }
        query.select(I.participationId, S.name, I.qty).from(I).join(S).on(S.id.eq(I.skuId)).where(I.participationId.in(participationIds))
                .orderBy(I.participationId.asc(), S.kind.desc(), S.sortOrder.asc()).fetch().forEach(t -> {
                    long id = t.get(I.participationId);
                    int q = n(t.get(I.qty));
                    parts.computeIfAbsent(id, k -> new ArrayList<>()).add(t.get(S.name) + " × " + q);
                    qty.merge(id, q, Integer::sum);
                });
        Map<Long, HoldLines> out = new LinkedHashMap<>();
        parts.forEach((id, list) -> out.put(id, new HoldLines(String.join(", ", list), qty.get(id))));
        return out;
    }

    private static BooleanExpression movementWhere(long groupBuyId, QtyMovementKind kind) {
        BooleanExpression where = M.groupBuyId.eq(groupBuyId);
        return kind == null ? where : where.and(M.kind.eq(kind));
    }

    public long movementCount(long groupBuyId, QtyMovementKind kind) {
        return n(query.select(M.count()).from(M).where(movementWhere(groupBuyId, kind)).fetchOne());
    }

    /** 수량 움직임 한 쪽 — 새 것 먼저 */
    public List<MovementRow> movements(long groupBuyId, QtyMovementKind kind, long offset, int limit) {
        return query.select(M.id, M.kind, S.name, S.kind, M.qty, M.currentAfter, M.reservedAfter, M.maxAfter, M.skuCurrentAfter, M.skuReservedAfter, M.skuMaxAfter,
                        PA.orderNo, O.operatorName, M.reason, M.createdAt, M.actorId)
                .from(M).leftJoin(S).on(S.id.eq(M.skuId)).leftJoin(PA).on(PA.id.eq(M.participationId)).leftJoin(O).on(O.id.eq(M.actorId))
                .where(movementWhere(groupBuyId, kind)).orderBy(M.id.desc()).offset(offset).limit(limit).fetch().stream()
                .map(t -> new MovementRow(t.get(M.id), t.get(M.kind), t.get(S.name), t.get(S.kind), n(t.get(M.qty)), t.get(M.currentAfter), t.get(M.reservedAfter),
                        t.get(M.maxAfter), t.get(M.skuCurrentAfter), t.get(M.skuReservedAfter), t.get(M.skuMaxAfter), t.get(PA.orderNo), t.get(O.operatorName),
                        t.get(M.reason), t.get(M.createdAt), t.get(M.actorId))).toList();
    }

    // ── 지연 안내·공급 불가 ─────────────────────────────────────────────────

    /** 발송 지연 안내 — 새 것 먼저. 안내 받은 주문 = 그 안내 전에 참여한 결제 계열 주문 */
    public List<DelayNoticeRow> delayNotices(long groupBuyId) {
        QGroupBuyParticipationEntity p = new QGroupBuyParticipationEntity("notifiedOrder");
        Expression<Long> notified = JPAExpressions.select(p.count()).from(p).where(p.groupBuyId.eq(D.groupBuyId), p.joinedAt.loe(D.createdAt),
                p.status.in(ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING, ParticipationStatus.CANCELED, ParticipationStatus.REFUNDED));
        return query.select(D.id, D.previousPlannedAt, D.newPlannedAt, D.reason, O.operatorName, D.createdAt, notified).from(D).leftJoin(O).on(O.id.eq(D.operatorId))
                .where(D.groupBuyId.eq(groupBuyId)).orderBy(D.createdAt.desc()).fetch().stream()
                .map(t -> new DelayNoticeRow(t.get(D.id), t.get(D.previousPlannedAt), t.get(D.newPlannedAt), t.get(D.reason), t.get(O.operatorName), t.get(D.createdAt),
                        (int) n(t.get(6, Long.class)))).toList();
    }

    /** 공급 불가 기록 — 새 것 먼저 */
    public List<SupplyFailureRow> supplyFailures(long groupBuyId) {
        QGroupBuyParticipationItemEntity i = new QGroupBuyParticipationItemEntity("canceledItem");
        QGroupBuySupplyChoiceEntity pending = new QGroupBuySupplyChoiceEntity("pendingChoice");
        Expression<Long> refundedLines = JPAExpressions.select(i.count()).from(i).where(i.skuId.eq(F.skuId), i.canceledQty.gt(0));
        Expression<Long> refundAmount = JPAExpressions.select(R.amount.sum().coalesce(0L)).from(R).where(R.groupBuyId.eq(F.groupBuyId), R.cause.eq(RefundCause.SUPPLY_FAILURE));
        Expression<Long> choices = JPAExpressions.select(CH.count()).from(CH).where(CH.supplyFailureId.eq(F.id));
        Expression<Long> pendingChoices = JPAExpressions.select(pending.count()).from(pending).where(pending.supplyFailureId.eq(F.id), pending.choice.isNull());
        return query.select(F.id, F.skuId, F.reason, F.choiceUntil, O.operatorName, F.createdAt, refundedLines, refundAmount, choices, pendingChoices).from(F)
                .leftJoin(O).on(O.id.eq(F.operatorId)).where(F.groupBuyId.eq(groupBuyId)).orderBy(F.createdAt.desc()).fetch().stream()
                .map(t -> new SupplyFailureRow(t.get(F.id), t.get(F.skuId), t.get(F.reason), t.get(F.choiceUntil), t.get(O.operatorName), t.get(F.createdAt),
                        (int) n(t.get(6, Long.class)), n(t.get(7, Long.class)), (int) n(t.get(8, Long.class)), (int) n(t.get(9, Long.class)))).toList();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** {@code sum(정수 칸)} — Hibernate 는 정수 합을 {@code Long} 으로 돌려준다 */
    static NumberExpression<Long> sumLong(Expression<? extends Number> value) {
        return Expressions.numberTemplate(Long.class, "sum({0})", value);
    }

    static long n(Long value) {
        return value == null ? 0 : value;
    }

    static int n(Integer value) {
        return value == null ? 0 : value;
    }
}

package com.community.pointshop.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseEntity;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundKind;
import com.community.pointshop.entity.PointShopRefundRequestEntity;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.QPointShopItemEntity;
import com.community.pointshop.entity.QPointShopPurchaseEntity;
import com.community.pointshop.entity.QPointShopRefundRequestEntity;
import com.community.pointshop.entity.QUserItemEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.jpa.UniqueViolations;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

/**
 * 포인트 상점 구매·환불 신청 읽기·쓰기 — {@code com.community.pointshop.service} 만 부른다. 트랜잭션은 부르는 서비스가 연다(포인트 사용·사용 취소와 한 트랜잭션).
 * 환불은 구매 행 잠금({@code FOR NO KEY UPDATE}) → 조건부 UPDATE({@code status = 'COMPLETED'})로 한 번만. 사본 칸은 DB 트리거가 고치지 못하게 막는다.
 * 모두 QueryDSL 이다 — 읽기는 한 줄 모양(프로젝션), 넣기는 넣고 바로 내보낸다(유일 위반은 {@code DuplicateKeyException} — {@link UniqueViolations}), 구매마다 가장 최근 신청은 {@code DISTINCT ON} 대신 {@code id IN (SELECT max(id) … GROUP BY)}.
 */
@Repository
public class PointShopPurchaseStore extends RoboyamRepositoryQDSL {

    /** 구매 한 줄 */
    public record PurchaseRow(long id, long userId, long itemId, String itemCode, String itemName, PointShopItemKind itemKind, PointShopUsageType usageType, int price,
                              Integer durationDays, PointShopPurchaseStatus status, Instant refundNoticeAgreedAt, long useLedgerId, Long refundLedgerId,
                              Instant refundedAt, Long refundedByOperatorId, String refundReason, Instant createdAt,
                              PointShopRefundKind refundKind) {
    }

    /** 관리자 구매 내역 한 줄 — 구매 + 회원(닉네임·탈퇴) + 보유 줄의 처음 사용 시각 + 아바타 ID + 예외 환불 운영자 이름 */
    public record AdminRow(PurchaseRow purchase, String nickname, boolean withdrawn, Instant firstUsedAt, String avatarId, String operatorName, Instant reservedAt) {
    }

    /**
     * 관리자 구매 내역 조건 — 모두 선택(null = 조건 없음). {@code q} 는 닉네임 일부 또는 회원번호. {@code refundRequest} 는 그 상태의 환불 신청이 있는 구매만
     * ({@code PENDING} = 「환불 신청 대기」)
     */
    public record AdminQuery(Instant from, Instant to, PointShopPurchaseStatus status, PointShopItemKind kind, String q, int page, int size,
                             PointShopRefundRequestStatus refundRequest) {
    }

    /** 환불 신청 한 줄(V87) */
    public record RequestRow(long id, long purchaseId, long userItemId, long userId, PointShopRefundRequestStatus status, Instant requestedAt, Instant decidedAt,
                             Long decidedByOperatorId, String rejectReason) {
    }

    private static final QPointShopPurchaseEntity P = QPointShopPurchaseEntity.pointShopPurchaseEntity;
    private static final QPointShopRefundRequestEntity R = QPointShopRefundRequestEntity.pointShopRefundRequestEntity;
    /** 하위 질의용 별칭 */
    private static final QPointShopRefundRequestEntity RX = new QPointShopRefundRequestEntity("latestRequest");
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;
    private static final QUserItemEntity UI = QUserItemEntity.userItemEntity;
    private static final QPointShopItemEntity I = QPointShopItemEntity.pointShopItemEntity;
    private static final QOperatorEntity O = new QOperatorEntity("refundOperator");

    private static final Expression<?>[] PURCHASE = {P.id, P.userId, P.itemId, P.itemCode, P.itemName, P.itemKind, P.usageType, P.pricePoint, P.durationDays, P.status,
            P.refundNoticeAgreedAt, P.useLedgerId, P.refundLedgerId, P.refundedAt, P.refundedByOperatorId, P.refundReason, P.createdAt, P.refundKind};
    private static final Expression<?>[] REQUEST = {R.id, R.purchaseId, R.userItemId, R.userId, R.status, R.requestedAt, R.decidedAt, R.decidedByOperatorId,
            R.rejectReason};

    private final PointShopPurchaseRepository purchases;
    private final PointShopRefundRequestRepository requests;

    public PointShopPurchaseStore(JPAQueryFactory query, PointShopPurchaseRepository purchases, PointShopRefundRequestRepository requests) {
        super(query);
        this.purchases = purchases;
        this.requests = requests;
    }

    public void insert(PurchaseRow p) {
        UniqueViolations.inserting(() -> {
            purchases.persist(PointShopPurchaseEntity.complete(p.id(), p.userId(), p.itemId(), p.itemCode(), p.itemName(), p.itemKind(), p.usageType(), p.price(),
                    p.durationDays(), p.refundNoticeAgreedAt(), p.useLedgerId(), p.createdAt()));
            purchases.flush();
        });
    }

    public Optional<PurchaseRow> find(long id) {
        return Optional.ofNullable(query.select(PURCHASE).from(P).where(P.id.eq(id)).fetchOne()).map(PointShopPurchaseStore::rowOf);
    }

    /** 환불 전에 — 구매 행을 잠그고 읽는다(셀프 환불·예외 환불이 겹쳐도 한 번만). 구매 표만 고르는 한 문장 */
    public Optional<PurchaseRow> findForUpdate(long id) {
        return Optional.ofNullable(query.select(PURCHASE).from(P).where(P.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne())
                .map(PointShopPurchaseStore::rowOf);
    }

    /**
     * 환불로 표시한다 — 아직 완료 상태일 때만(조건부).
     *
     * @param kind       환불 종류(셀프 · 예외 · 신청 수락)
     * @param operatorId 처리 운영자(셀프 환불은 null)
     * @param reason     예외 환불 사유(그 밖은 null)
     * @return 바꿨으면 true
     */
    public boolean markRefunded(long id, long refundLedgerId, Instant now, PointShopRefundKind kind, Long operatorId, String reason) {
        return query.update(P).set(P.status, PointShopPurchaseStatus.REFUNDED).set(P.refundLedgerId, refundLedgerId).set(P.refundedAt, now).set(P.refundKind, kind)
                .set(P.refundedByOperatorId, operatorId).set(P.refundReason, reason)
                .where(P.id.eq(id), P.status.eq(PointShopPurchaseStatus.COMPLETED)).execute() == 1;
    }

    // ── 환불 신청(V87) ──────────────────────────────────────────────────────

    public void insertRequest(long id, long purchaseId, long userItemId, long userId, Instant now) {
        UniqueViolations.inserting(() -> {
            requests.persist(PointShopRefundRequestEntity.pending(id, purchaseId, userItemId, userId, now));
            requests.flush();
        });
    }

    public Optional<RequestRow> findRequest(long id) {
        return Optional.ofNullable(query.select(REQUEST).from(R).where(R.id.eq(id)).fetchOne()).map(PointShopPurchaseStore::requestOf);
    }

    /** 결정 전에 — 신청 행을 잠그고 읽는다(구매 행을 잠근 뒤에). 신청 표만 고르는 한 문장 */
    public Optional<RequestRow> findRequestForUpdate(long id) {
        return Optional.ofNullable(query.select(REQUEST).from(R).where(R.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne())
                .map(PointShopPurchaseStore::requestOf);
    }

    /** 그 구매의 가장 최근 신청(없으면 빈 값) */
    public Optional<RequestRow> latestRequest(long purchaseId) {
        return Optional.ofNullable(query.select(REQUEST).from(R).where(R.purchaseId.eq(purchaseId)).orderBy(R.id.desc()).limit(1).fetchFirst())
                .map(PointShopPurchaseStore::requestOf);
    }

    /** 구매마다 가장 최근 신청(구매마다 가장 큰 신청 ID) */
    public Map<Long, RequestRow> latestRequests(Collection<Long> purchaseIds) {
        Map<Long, RequestRow> out = new HashMap<>();
        if (purchaseIds.isEmpty()) {
            return out;
        }
        query.select(REQUEST).from(R).where(R.id.in(JPAExpressions.select(RX.id.max()).from(RX).where(RX.purchaseId.in(purchaseIds)).groupBy(RX.purchaseId))).fetch()
                .forEach(t -> {
                    RequestRow row = requestOf(t);
                    out.put(row.purchaseId(), row);
                });
        return out;
    }

    /** 그 구매에 그 상태의 신청이 있나 */
    public boolean hasRequest(long purchaseId, PointShopRefundRequestStatus status) {
        return query.selectOne().from(R).where(R.purchaseId.eq(purchaseId), R.status.eq(status)).fetchFirst() != null;
    }

    /** 대기 중인 신청을 결정한다(조건부 — 대기일 때만) */
    public boolean decide(long id, PointShopRefundRequestStatus status, long operatorId, String rejectReason, Instant now) {
        return query.update(R).set(R.status, status).set(R.decidedAt, now).set(R.decidedByOperatorId, operatorId).set(R.rejectReason, rejectReason)
                .where(R.id.eq(id), R.status.eq(PointShopRefundRequestStatus.PENDING)).execute() == 1;
    }

    /** 관리자 구매 내역 — 최신 구매가 위(ID 내림차순), 쪽마다 {@code size} 줄 */
    public List<AdminRow> adminPage(AdminQuery q) {
        Expression<?>[] columns = Stream.concat(Stream.of(PURCHASE), Stream.of(PR.nickname, U.id, U.status, U.deletedAt, UI.firstUsedAt, I.avatarId, O.operatorName,
                UI.reservedAt)).toArray(Expression<?>[]::new);
        return adminFrom(query.select(columns)).where(adminWhere(q)).orderBy(P.id.desc()).offset((long) q.page() * q.size()).limit(q.size()).fetch().stream()
                .map(t -> new AdminRow(rowOf(t), t.get(PR.nickname), withdrawn(t), t.get(UI.firstUsedAt), t.get(I.avatarId), t.get(O.operatorName), t.get(UI.reservedAt)))
                .toList();
    }

    public long adminCount(AdminQuery q) {
        Long count = adminFrom(query.select(P.count())).where(adminWhere(q)).fetchOne();
        return count == null ? 0 : count;
    }

    private static <T> JPAQuery<T> adminFrom(JPAQuery<T> select) {
        return select.from(P).leftJoin(U).on(U.id.eq(P.userId)).leftJoin(PR).on(PR.userId.eq(P.userId)).leftJoin(UI).on(UI.purchaseId.eq(P.id))
                .leftJoin(I).on(I.id.eq(P.itemId)).leftJoin(O).on(O.id.eq(P.refundedByOperatorId));
    }

    private static BooleanBuilder adminWhere(AdminQuery q) {
        BooleanBuilder where = new BooleanBuilder();
        if (q.from() != null) {
            where.and(P.createdAt.goe(q.from()));
        }
        if (q.to() != null) {
            where.and(P.createdAt.lt(q.to()));
        }
        if (q.status() != null) {
            where.and(P.status.eq(q.status()));
        }
        if (q.kind() != null) {
            where.and(P.itemKind.eq(q.kind()));
        }
        if (q.refundRequest() != null) {
            where.and(JPAExpressions.selectOne().from(RX).where(RX.purchaseId.eq(P.id), RX.status.eq(q.refundRequest())).exists());
        }
        String text = q.q() == null ? "" : q.q().strip();
        if (!text.isEmpty()) {
            String like = "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            if (text.matches("\\d{1,19}")) {
                where.and(P.userId.eq(Long.parseLong(text)).or(PR.nickname.lower().like(like, '\\')));
            } else {
                where.and(PR.nickname.lower().like(like, '\\'));
            }
        }
        return where;
    }

    /** 회원 행이 없거나 탈퇴·삭제 */
    private static boolean withdrawn(Tuple t) {
        return t.get(U.id) == null || t.get(U.status) == UserStatus.WITHDRAWN || t.get(U.deletedAt) != null;
    }

    private static PurchaseRow rowOf(Tuple t) {
        return new PurchaseRow(t.get(P.id), t.get(P.userId), t.get(P.itemId), t.get(P.itemCode), t.get(P.itemName), t.get(P.itemKind), t.get(P.usageType),
                t.get(P.pricePoint), t.get(P.durationDays), t.get(P.status), t.get(P.refundNoticeAgreedAt), t.get(P.useLedgerId), t.get(P.refundLedgerId),
                t.get(P.refundedAt), t.get(P.refundedByOperatorId), t.get(P.refundReason), t.get(P.createdAt), t.get(P.refundKind));
    }

    private static RequestRow requestOf(Tuple t) {
        return new RequestRow(t.get(R.id), t.get(R.purchaseId), t.get(R.userItemId), t.get(R.userId), t.get(R.status), t.get(R.requestedAt), t.get(R.decidedAt),
                t.get(R.decidedByOperatorId), t.get(R.rejectReason));
    }
}

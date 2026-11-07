package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.ExpireCause;
import com.community.groupbuy.entity.GroupBuyParticipationEntity;
import com.community.groupbuy.entity.GroupBuyParticipationItemEntity;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QGroupBuyDelayNoticeEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationItemEntity;
import com.community.groupbuy.entity.QGroupBuyRequestEntity;
import com.community.groupbuy.entity.QGroupBuyRequestItemEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.QParticipationStatusHistoryEntity;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.SkuKind;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * 주문({@code group_buy_participations})·주문 줄·주문 상태 이력·주문 번호. 상태 전이는 한 문장 조건부 UPDATE(이긴 쪽만 — 선점 만료 워커·결제 확정·중단·본인 취소가 겨룬다).
 *
 * <p>QueryDSL 이 기본이다(읽기는 한 줄 모양, 쓰기는 즉시 실행 — {@link GroupBuyStoreSupport}). 주문 행 잠금은 주문 행만 고르는 잠금 문장 뒤에 읽는다.
 * Native SQL 로 남긴 것과 이유: 주문 번호 하루 일련({@code INSERT … ON CONFLICT … DO UPDATE … RETURNING} — 서버 여러 대가 같은 날 번호를 겹치지 않게 한 문장에서 올리고 받는다),
 * 성사 공구 JOINED → CONFIRMED 일괄({@code UPDATE … WHERE id IN (… LIMIT) RETURNING} — 바꾼 주문을 같은 문장에서 받는다), 운영 환불 줄 내역 합({@code jsonb_array_elements}),
 * 결제 확정 세이브포인트 안의 쓰기({@link #markPaid} · {@link #expire} · {@link #insertHistory} — JDBC: 그 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용).
 */
@Repository
public class ParticipationStore extends GroupBuyStoreSupport {

    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyParticipationItemEntity I = QGroupBuyParticipationItemEntity.groupBuyParticipationItemEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QParticipationStatusHistoryEntity HIS = QParticipationStatusHistoryEntity.participationStatusHistoryEntity;
    private static final QGroupBuyRequestEntity RQ = QGroupBuyRequestEntity.groupBuyRequestEntity;
    private static final QGroupBuyRequestItemEntity RI = QGroupBuyRequestItemEntity.groupBuyRequestItemEntity;
    private static final QGroupBuyDelayNoticeEntity DN = QGroupBuyDelayNoticeEntity.groupBuyDelayNoticeEntity;

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /** 주문 한 줄 */
    public record ParticipationRow(long id, String orderNo, long groupBuyId, long userId, ParticipationStatus status, long itemsAmount, long shippingFee, long amount,
                                   long pointAmount, long cashAmount, long refundedAmount, long compensationAmount, long reshippingFeeCharged, int groupBuyRevision,
                                   Long paymentOrderId, Instant reservedUntil, ExpireCause expireCause, Instant joinedAt, Instant createdAt,
                                   Long shippedShippingFee, Instant shippedAt, Instant deliveredAt, Instant addressMaskedAt) {

        /** 남은 결제액 = 현금 − 환불 누계 − 보상 누계(결정 38) */
        public long remainingCash() {
            return cashAmount - refundedAmount - compensationAmount;
        }
    }

    private static final ConstructorExpression<ParticipationRow> ROW = Projections.constructor(ParticipationRow.class, PA.id, PA.orderNo, PA.groupBuyId, PA.userId,
            PA.status, PA.itemsAmount, PA.shippingFee, PA.amount, PA.pointAmount, PA.cashAmount, PA.refundedAmount, PA.compensationAmount, PA.reshippingFeeCharged,
            PA.groupBuyRevision, PA.paymentOrderId, PA.reservedUntil, PA.expireCause, PA.joinedAt, PA.createdAt, PA.shippedShippingFee, PA.shippedAt, PA.deliveredAt,
            PA.addressMaskedAt);

    /** 주문 줄 한 줄 */
    public record ItemRow(long id, long participationId, long groupBuyId, long skuId, boolean main, int qty, long unitPrice, long itemAmount, int canceledQty, int returnedQty,
                          int restockedQty) {

        /** 아직 가진(취소·반품하지 않은) 수량 */
        public int heldQty() {
            return qty - canceledQty - returnedQty;
        }
    }

    /** 새 주문 */
    public record NewParticipation(long id, String orderNo, long groupBuyId, long userId, long itemsAmount, long shippingFee, long amount, int groupBuyRevision,
                                   Instant reservedUntil, String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc,
                                   Instant now) {
    }

    public ParticipationStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    // ── 주문 번호 ───────────────────────────────────────────────────────────

    /** {@code G} + KST 날짜 + 그날 일련 5자리(결정 13 — 넘으면 자리가 는다). Native SQL 인 이유: {@code ON CONFLICT … DO UPDATE … RETURNING} 원자적 일련 */
    public String nextOrderNo(LocalDate day) {
        Integer seq = jdbc.queryForObject("INSERT INTO group_buy_order_counters (day, last_seq) VALUES (?, 1) ON CONFLICT (day) DO UPDATE "
                + "SET last_seq = group_buy_order_counters.last_seq + 1 RETURNING last_seq", Integer.class, Date.valueOf(day));
        return "G" + DAY.format(day) + String.format("%05d", seq);
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    private JPAQuery<ParticipationRow> rows() {
        return query.select(ROW).from(PA);
    }

    public Optional<ParticipationRow> find(long id) {
        return Optional.ofNullable(rows().where(PA.id.eq(id)).fetchFirst());
    }

    /** 주문 행을 잠그고 읽는다 — 결제가 PAID 인 흐름(취소를 넣는 흐름)만 주문 행을 먼저 잠근다(결정 37) */
    public Optional<ParticipationRow> lock(long id) {
        return lockRow(PA, PA.id, id) ? find(id) : Optional.empty();
    }

    /** 잠겨 있으면 건너뛴다(환불 워커 — 서버 여럿이 나눠 잡는다, 결정 61) */
    public Optional<ParticipationRow> lockSkipLocked(long id) {
        return lockRowSkipLocked(PA, PA.id, id) ? find(id) : Optional.empty();
    }

    public List<ItemRow> items(long participationId) {
        return query.select(I.id, I.participationId, I.groupBuyId, I.skuId, S.kind, I.qty, I.unitPrice, I.itemAmount, I.canceledQty, I.returnedQty, I.restockedQty).from(I)
                .join(S).on(S.id.eq(I.skuId)).where(I.participationId.eq(participationId)).orderBy(I.skuId.asc()).fetch().stream()
                .map(t -> new ItemRow(t.get(I.id), t.get(I.participationId), t.get(I.groupBuyId), t.get(I.skuId), t.get(S.kind) == SkuKind.MAIN, n(t.get(I.qty)),
                        n(t.get(I.unitPrice)), n(t.get(I.itemAmount)), n(t.get(I.canceledQty)), n(t.get(I.returnedQty)), n(t.get(I.restockedQty))))
                .toList();
    }

    /** 그 회원의 그 공구 진행 중 주문(PENDING_PAYMENT~SHIPPED) */
    public Optional<ParticipationRow> inProgress(long groupBuyId, long userId) {
        return Optional.ofNullable(rows().where(PA.groupBuyId.eq(groupBuyId), PA.userId.eq(userId), PA.status.in(ParticipationStatus.IN_PROGRESS)).fetchFirst());
    }

    /**
     * 1인 한도에 쓴 수량 — 그 회원의 Σ(qty − 취소 − 반품)(반품은 처리 방법과 관계없이 뺀다) + 진행 중 선점(결제 중 줄은 취소·반품 0 이라 같은 식). 만료 주문은 세지 않는다(결정 28).
     */
    public int limitUsed(long userId, List<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return 0;
        }
        Long n = query.select(sumLong(I.qty.subtract(I.canceledQty).subtract(I.returnedQty))).from(I).join(S).on(S.id.eq(I.skuId), S.kind.eq(SkuKind.MAIN))
                .join(PA).on(PA.id.eq(I.participationId)).where(PA.userId.eq(userId), PA.status.ne(ParticipationStatus.EXPIRED), PA.groupBuyId.in(groupBuyIds)).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /** 선점 시간이 지난 결제 중 주문(선점 만료 워커) */
    public List<Long> overdueHolds(Instant now, int limit) {
        return query.select(PA.id).from(PA).where(PA.status.eq(ParticipationStatus.PENDING_PAYMENT), PA.reservedUntil.lt(now)).orderBy(PA.reservedUntil.asc(), PA.id.asc())
                .limit(limit).fetch();
    }

    /** 그 회원의 그 공구 주문 중 선점 시간이 지난 결제 중 주문(조회 때 정리는 본인 주문만 — 결정 85) */
    public List<Long> ownOverdueHolds(long userId, long groupBuyId, Instant now) {
        return query.select(PA.id).from(PA)
                .where(PA.userId.eq(userId), PA.groupBuyId.eq(groupBuyId), PA.status.eq(ParticipationStatus.PENDING_PAYMENT), PA.reservedUntil.lt(now)).fetch();
    }

    /** 불발·중단 공구의 비종결 주문(환불 워커 — 결정 61) */
    public List<Long> unsettledInClosedGroups(int limit) {
        return query.select(PA.id).from(PA).join(G).on(G.id.eq(PA.groupBuyId))
                .where(G.status.in(GroupBuyStatus.CLOSED_FAILED, GroupBuyStatus.CANCELED),
                        PA.status.in(ParticipationStatus.PENDING_PAYMENT, ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING))
                .orderBy(PA.id.asc()).limit(limit).fetch();
    }

    /** 최근 HOLD_TIMEOUT 만료 횟수(선점 반복 이탈 — 결정 28) */
    public int holdTimeouts(long userId, Instant since) {
        Long n = query.select(HIS.count()).from(HIS)
                .where(HIS.userId.eq(userId), HIS.toStatus.eq(ParticipationStatus.EXPIRED), HIS.cause.eq(ExpireCause.HOLD_TIMEOUT.name()), HIS.createdAt.gt(since)).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    // ── 쓰기 ────────────────────────────────────────────────────────────────

    public void insert(NewParticipation p) {
        insert(GroupBuyParticipationEntity.pending(p.id(), p.orderNo(), p.groupBuyId(), p.userId(), p.itemsAmount(), p.shippingFee(), p.amount(), p.groupBuyRevision(),
                p.reservedUntil(), p.recipientEnc(), p.phoneEnc(), p.postcode(), p.address1Enc(), p.address2Enc(), p.memoEnc(), p.now()));
    }

    public void insertItem(long id, long participationId, long groupBuyId, long skuId, int qty, long unitPrice) {
        insert(GroupBuyParticipationItemEntity.create(id, participationId, groupBuyId, skuId, qty, unitPrice));
    }

    /** 결제 시도와 선점 끝을 붙인다(결제 중일 때만 — 결정 41) */
    public boolean attachPayment(long id, long paymentOrderId, Instant reservedUntil, Instant now) {
        return query.update(PA).set(PA.paymentOrderId, paymentOrderId).set(PA.reservedUntil, reservedUntil).set(PA.updatedAt, now)
                .where(PA.id.eq(id), PA.status.eq(ParticipationStatus.PENDING_PAYMENT)).execute() == 1;
    }

    /**
     * 결제 중 → 만료(원인). {@code dueBefore} 가 있으면 선점 시간이 그 전에 끝났을 때만(이긴 쪽만 true).
     * JDBC 인 이유: 결제 확정({@code onPaid})의 JDBC 세이브포인트 안에서 도는 쓰기 — 세이브포인트 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용이 되어 거절로 되돌릴 수 없다(G25 ③).
     */
    public boolean expire(long id, ExpireCause cause, Instant dueBefore, Instant now) {
        if (dueBefore == null) {
            return jdbc.update("UPDATE group_buy_participations SET status = 'EXPIRED', expire_cause = ?, expired_at = ?, updated_at = ? WHERE id = ? "
                    + "AND status = 'PENDING_PAYMENT'", cause.name(), ts(now), ts(now), id) == 1;
        }
        return jdbc.update("UPDATE group_buy_participations SET status = 'EXPIRED', expire_cause = ?, expired_at = ?, updated_at = ? WHERE id = ? "
                + "AND status = 'PENDING_PAYMENT' AND reserved_until < ?", cause.name(), ts(now), ts(now), id, ts(dueBefore)) == 1;
    }

    /** 결제 중 → JOINED(마감 후 발주) 또는 CONFIRMED(즉시 발송 — 한 번에). JDBC 인 이유: 결제 확정({@code onPaid})의 JDBC 세이브포인트 안에서 도는 쓰기 — 세이브포인트 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용이 되어 거절로 되돌릴 수 없다(G25 ③) */
    public boolean markPaid(long id, ParticipationStatus to, long paymentOrderId, Instant paidAt, int revision, Instant now) {
        return jdbc.update("UPDATE group_buy_participations SET status = ?, payment_order_id = ?, joined_at = ?, confirmed_at = ?, group_buy_revision = ?, updated_at = ? "
                        + "WHERE id = ? AND status = 'PENDING_PAYMENT'", to.name(), paymentOrderId, ts(paidAt), to == ParticipationStatus.CONFIRMED ? ts(paidAt) : null,
                revision, ts(now), id) == 1;
    }

    /** 결제된 주문의 상태를 바꾼다(지금 상태가 {@code from} 중 하나일 때만) — CANCELED·REFUNDED 면 그 시각도 */
    public boolean transition(long id, Set<ParticipationStatus> from, ParticipationStatus to, Instant now) {
        JPAUpdateClause update = query.update(PA).set(PA.status, to).set(PA.updatedAt, now);
        switch (to) {
            case CANCELED -> update.set(PA.canceledAt, now);
            case REFUNDED -> update.set(PA.refundedAt, now);
            case CONFIRMED -> update.set(PA.confirmedAt, now);
            default -> {
            }
        }
        return update.where(PA.id.eq(id), PA.status.in(from)).execute() == 1;
    }

    /**
     * 성사 판정 뒤 JOINED → CONFIRMED 일괄(판정 ①과 따로 — 공구 행을 쥐지 않는다). 바뀐 주문 {id, 공구, user}.
     * Native SQL 인 이유: 일괄 {@code UPDATE … RETURNING} — 이긴 줄만 같은 문장에서 받는다(알림 대상).
     */
    public List<long[]> confirmJoinedOfSucceeded(Instant now, int limit) {
        return jdbc.query("UPDATE group_buy_participations p SET status = 'CONFIRMED', confirmed_at = ?, updated_at = ? WHERE p.id IN (SELECT q.id FROM group_buy_participations q "
                        + "JOIN group_buys g ON g.id = q.group_buy_id WHERE q.status = 'JOINED' AND g.status IN ('CLOSED_SUCCESS', 'ORDERED', 'SHIPPING') ORDER BY q.id LIMIT ?) "
                        + "AND p.status = 'JOINED' RETURNING p.id, p.group_buy_id, p.user_id", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)},
                Timestamp.from(now), Timestamp.from(now), limit);
    }

    /** 줄의 취소 수량을 올린다(아직 가진 수량 안에서만) */
    public boolean cancelItemQty(long itemId, int qty) {
        return query.update(I).set(I.canceledQty, I.canceledQty.add(qty)).where(I.id.eq(itemId), I.canceledQty.add(I.returnedQty).add(qty).loe(I.qty)).execute() == 1;
    }

    /** 환불·보상 누계를 올린다 — 합쳐 남은 결제액을 넘지 않을 때만(결정 38·39 — 공구가 상한의 정본) */
    public boolean addRefunded(long id, long refund, long compensation, Instant now) {
        return query.update(PA).set(PA.refundedAmount, PA.refundedAmount.add(refund)).set(PA.compensationAmount, PA.compensationAmount.add(compensation))
                .set(PA.updatedAt, now)
                .where(PA.id.eq(id), PA.refundedAmount.add(PA.compensationAmount).add(refund).add(compensation).loe(PA.cashAmount),
                        PA.refundedAmount.add(refund).loe(PA.amount))
                .execute() == 1;
    }

    /** 재부과 배송비 누계(결정 33·42) */
    public void addReshippingCharged(long id, long fee, Instant now) {
        query.update(PA).set(PA.reshippingFeeCharged, PA.reshippingFeeCharged.add(fee)).set(PA.updatedAt, now).where(PA.id.eq(id)).execute();
    }

    /** 주문 상태 이력 한 줄. JDBC 인 이유: 결제 확정({@code onPaid})의 JDBC 세이브포인트 안에서 도는 쓰기 — 세이브포인트 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용이 되어 거절로 되돌릴 수 없다(G25 ③)(주문 상태 이력은 그 안에서 넣는다) */
    public void insertHistory(long id, ParticipationRow p, ParticipationStatus from, ParticipationStatus to, String cause, Long actorId, Instant now) {
        insertHistory(id, p.id(), p.groupBuyId(), p.userId(), from, to, cause, actorId, now);
    }

    public void insertHistory(long id, long participationId, long groupBuyId, long userId, ParticipationStatus from, ParticipationStatus to, String cause, Instant now) {
        insertHistory(id, participationId, groupBuyId, userId, from, to, cause, null, now);
    }

    private void insertHistory(long id, long participationId, long groupBuyId, long userId, ParticipationStatus from, ParticipationStatus to, String cause, Long actorId,
                               Instant now) {
        jdbc.update("INSERT INTO participation_status_history (id, participation_id, group_buy_id, user_id, from_status, to_status, cause, actor_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, participationId, groupBuyId, userId, from == null ? null : from.name(), to.name(), cause, actorId, ts(now));
    }

    // ── 발송·반품(2b-3) ──────────────────────────────────────────────────────

    /** 준비중(CONFIRMED → PREPARING — 운영자가 포장을 시작했다, 10-3) */
    public boolean prepare(long id, Instant now) {
        return query.update(PA).set(PA.status, ParticipationStatus.PREPARING).set(PA.updatedAt, now)
                .where(PA.id.eq(id), PA.status.eq(ParticipationStatus.CONFIRMED)).execute() == 1;
    }

    /** 발송(CONFIRMED·PREPARING → SHIPPED) — 처음 배송비를 고정한다: 낸 배송비 + 그때까지의 재부과(결정 43) */
    public boolean ship(long id, Instant now) {
        return query.update(PA).set(PA.status, ParticipationStatus.SHIPPED).set(PA.shippedAt, now).set(PA.shippedShippingFee, PA.shippingFee.add(PA.reshippingFeeCharged))
                .set(PA.updatedAt, now).where(PA.id.eq(id), PA.status.in(ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING)).execute() == 1;
    }

    /** 배송완료(SHIPPED → DELIVERED — 청약철회 7일의 시작, 결정 7) */
    public boolean deliver(long id, Instant now) {
        return query.update(PA).set(PA.status, ParticipationStatus.DELIVERED).set(PA.deliveredAt, now).set(PA.updatedAt, now)
                .where(PA.id.eq(id), PA.status.eq(ParticipationStatus.SHIPPED)).execute() == 1;
    }

    /** 줄의 반품 수량을 올린다(회수 확인 때 — 결정 83, 아직 가진 수량 안에서만) */
    public boolean returnItemQty(long itemId, int qty) {
        return query.update(I).set(I.returnedQty, I.returnedQty.add(qty)).where(I.id.eq(itemId), I.canceledQty.add(I.returnedQty).add(qty).loe(I.qty)).execute() == 1;
    }

    /** 다시 판매 수량(반품 수량 안에서만 — 결정 15) */
    public boolean restockItemQty(long itemId, int qty) {
        return query.update(I).set(I.restockedQty, I.restockedQty.add(qty)).where(I.id.eq(itemId), I.restockedQty.add(qty).loe(I.returnedQty)).execute() == 1;
    }

    /** 교환 출고 수량(기록만 — 결정 54) */
    public void exchangeItemQty(long itemId, int qty) {
        query.update(I).set(I.exchangedQty, I.exchangedQty.add(qty)).where(I.id.eq(itemId)).execute();
    }

    /** 재부과 누계를 고친다(반품의 재부과 − 돌려줌, 음수 가능 — 결정 33·43) */
    public void addReshippingDelta(long id, long delta, Instant now) {
        if (delta != 0) {
            addReshippingCharged(id, delta, now);
        }
    }

    /** 배송지 암호문(가린 값·전체 보기·명단 내보내기는 서비스가 푼다) */
    public record AddressRow(long participationId, String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc,
                             Instant maskedAt) {
    }

    private static final ConstructorExpression<AddressRow> ADDRESS = Projections.constructor(AddressRow.class, PA.id, PA.recipientEnc, PA.phoneEnc, PA.postcode,
            PA.address1Enc, PA.address2Enc, PA.memoEnc, PA.addressMaskedAt);

    public Optional<AddressRow> address(long id) {
        return Optional.ofNullable(query.select(ADDRESS).from(PA).where(PA.id.eq(id)).fetchFirst());
    }

    /** 배송지 수정(파기되지 않은 주문만 — 기한은 서비스, 결정 30) */
    public boolean updateAddress(long id, String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc, Instant now) {
        return query.update(PA).set(PA.recipientEnc, recipientEnc).set(PA.phoneEnc, phoneEnc).set(PA.postcode, postcode).set(PA.address1Enc, address1Enc)
                .set(PA.address2Enc, address2Enc).set(PA.memoEnc, memoEnc).set(PA.updatedAt, now).where(PA.id.eq(id), PA.addressMaskedAt.isNull()).execute() == 1;
    }

    /** 배송 명단 대상 — 발송 전 결제된 주문(CONFIRMED·PREPARING — 결정 30) */
    public List<AddressRow> exportAddresses(long groupBuyId) {
        return query.select(ADDRESS).from(PA).where(PA.groupBuyId.eq(groupBuyId), PA.status.in(ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING))
                .orderBy(PA.id.asc()).fetch();
    }

    /** 그 공구에서 그 상태의 주문 */
    public List<ParticipationRow> ofGroup(long groupBuyId, Set<ParticipationStatus> statuses) {
        return rows().where(PA.groupBuyId.eq(groupBuyId), PA.status.in(statuses)).orderBy(PA.id.asc()).fetch();
    }

    /** 중단 요약 — 환불할 발송 전 주문 수·그 남은 결제액 합, 이미 발송한 주문 수, 결제 중 주문 수 */
    public record CancelCounts(int refundOrders, long refundAmount, int shippedOrders, int pendingOrders) {
    }

    /** 중단 확인 창의 숫자(결정 29) — 한 문장, 상태별 합은 CASE 로 */
    public CancelCounts cancelCounts(long groupBuyId) {
        com.querydsl.core.types.dsl.BooleanExpression beforeShipping = PA.status.in(ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING);
        com.querydsl.core.types.dsl.NumberExpression<Long> refundOrders = sumLong(new CaseBuilder().when(beforeShipping).then(1L).otherwise(0L));
        com.querydsl.core.types.dsl.NumberExpression<Long> refundAmount = sumLong(new CaseBuilder().when(beforeShipping)
                .then(PA.cashAmount.subtract(PA.refundedAmount).subtract(PA.compensationAmount)).otherwise(0L));
        com.querydsl.core.types.dsl.NumberExpression<Long> shipped = sumLong(new CaseBuilder()
                .when(PA.status.in(ParticipationStatus.SHIPPED, ParticipationStatus.DELIVERED)).then(1L).otherwise(0L));
        com.querydsl.core.types.dsl.NumberExpression<Long> pending = sumLong(new CaseBuilder().when(PA.status.eq(ParticipationStatus.PENDING_PAYMENT)).then(1L).otherwise(0L));
        com.querydsl.core.Tuple t = query.select(refundOrders, refundAmount, shipped, pending).from(PA).where(PA.groupBuyId.eq(groupBuyId)).fetchOne();
        return t == null ? new CancelCounts(0, 0, 0, 0)
                : new CancelCounts(Math.toIntExact(n(t.get(refundOrders))), n(t.get(refundAmount)), Math.toIntExact(n(t.get(shipped))), Math.toIntExact(n(t.get(pending))));
    }

    /**
     * 발송 기한 재료(결정 51) — 발송 전 주문(CONFIRMED·PREPARING)마다 공구의 발송 방식·예정일·영업일, 결제(확정) 시각, 그 뒤 마지막 발송 지연 공지의 새 예정일.
     * {@code participationId} 가 있으면 그 주문만. 지연 공지는 공구마다 한 번에 읽어 주문마다 「결제 시각 이후 가장 늦게 낸 공지」를 고른다(예전 {@code ORDER BY created_at DESC LIMIT 1} 서브쿼리와 같은 값).
     */
    public List<ShipDueRow> shipDueRows(Long participationId) {
        com.querydsl.core.types.dsl.DateTimeExpression<Instant> paid = PA.confirmedAt.coalesce(PA.joinedAt);
        JPAQuery<com.querydsl.core.Tuple> q = query.select(PA.id, PA.groupBuyId, G.fulfillment, G.shipPlannedAt, G.shipWithinBusinessDays, paid).from(PA).join(G)
                .on(G.id.eq(PA.groupBuyId)).where(PA.status.in(ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING));
        if (participationId != null) {
            q.where(PA.id.eq(participationId));
        }
        List<com.querydsl.core.Tuple> rows = q.fetch();
        if (rows.isEmpty()) {
            return List.of();
        }
        // 지연 안내는 따로 읽는다(예전 한 문장의 LIMIT 1 하위 질의 — JPQL 하위 질의에 LIMIT 이 없다). 안내 표는 넣기만 하는 기록이라 사이에 커밋된 안내는 「더 늦은 기한」을 보탤 뿐이다
        // (쓰는 곳은 발송 기한 경고 수·주문 목록 거르기·주문 상세 표시뿐 — 상태를 바꾸는 판정에는 쓰지 않는다)
        Map<Long, List<com.querydsl.core.Tuple>> notices = new HashMap<>();
        query.select(DN.groupBuyId, DN.createdAt, DN.newPlannedAt).from(DN).where(DN.groupBuyId.in(rows.stream().map(t -> t.get(PA.groupBuyId)).distinct().toList()))
                .orderBy(DN.createdAt.desc()).fetch().forEach(t -> notices.computeIfAbsent(t.get(DN.groupBuyId), k -> new java.util.ArrayList<>()).add(t));
        return rows.stream().map(t -> {
            Instant paidAt = t.get(paid);
            LocalDate delayed = paidAt == null ? null : notices.getOrDefault(t.get(PA.groupBuyId), List.of()).stream()
                    .filter(n -> !n.get(DN.createdAt).isBefore(paidAt)).findFirst().map(n -> n.get(DN.newPlannedAt)).orElse(null);
            return new ShipDueRow(t.get(PA.id), t.get(PA.groupBuyId), t.get(G.fulfillment), t.get(G.shipPlannedAt), t.get(G.shipWithinBusinessDays), paidAt, delayed);
        }).toList();
    }

    /** 발송 기한 재료 한 줄 */
    public record ShipDueRow(long participationId, long groupBuyId, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays, Instant paidAt,
                             LocalDate delayedPlannedAt) {
    }

    /**
     * 줄마다 금액만 돌려준 운영 환불 중 아직 반품 환불에서 빼지 않은 몫(결정 71) — 운영 환불 기록의 줄 내역({@code amountOnly}) 합 − 끝난 반품 요청 줄의 {@code prior_refund_offset} 합.
     * 앞 합은 Native SQL — 이유: 환불 기록의 줄 내역 jsonb 를 {@code jsonb_array_elements} 로 펼쳐 더한다.
     */
    public Map<Long, Long> priorRefundLeft(long participationId) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT (l->>'itemId')::bigint, sum((l->>'amount')::bigint) FROM group_buy_refunds r, jsonb_array_elements(r.lines) l WHERE r.participation_id = ? "
                + "AND r.cause = 'ADMIN' AND coalesce((l->>'amountOnly')::boolean, false) GROUP BY 1", (RowCallbackHandler) rs -> {
            out.merge(rs.getLong(1), rs.getLong(2), Long::sum);
        }, participationId);
        query.select(RI.participationItemId, sumLong(RI.priorRefundOffset)).from(RI).join(RQ).on(RQ.id.eq(RI.requestId))
                .where(RQ.participationId.eq(participationId), RQ.status.eq(RequestStatus.COMPLETED)).groupBy(RI.participationItemId).fetch()
                .forEach(t -> out.merge(t.get(0, Long.class), -n(t.get(1, Long.class)), Long::sum));
        return out;
    }
}

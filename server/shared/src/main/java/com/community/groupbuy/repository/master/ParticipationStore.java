package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.instant;
import static com.community.groupbuy.repository.master.GroupBuyStore.marks;
import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import com.community.groupbuy.entity.ExpireCause;
import com.community.groupbuy.entity.ParticipationStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 주문({@code group_buy_participations})·주문 줄·주문 상태 이력·주문 번호. 상태 전이는 한 문장 조건부 UPDATE(이긴 쪽만 — 선점 만료 워커·결제 확정·중단·본인 취소가 겨룬다).
 */
@Repository
public class ParticipationStore {

    private static final String COLUMNS = "id, order_no, group_buy_id, user_id, status, items_amount, shipping_fee, amount, point_amount, cash_amount, refunded_amount, "
            + "compensation_amount, reshipping_fee_charged, group_buy_revision, payment_order_id, reserved_until, expire_cause, joined_at, created_at";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /** 주문 한 줄 */
    public record ParticipationRow(long id, String orderNo, long groupBuyId, long userId, ParticipationStatus status, long itemsAmount, long shippingFee, long amount,
                                   long pointAmount, long cashAmount, long refundedAmount, long compensationAmount, long reshippingFeeCharged, int groupBuyRevision,
                                   Long paymentOrderId, Instant reservedUntil, ExpireCause expireCause, Instant joinedAt, Instant createdAt) {

        /** 남은 결제액 = 현금 − 환불 누계 − 보상 누계(결정 38) */
        public long remainingCash() {
            return cashAmount - refundedAmount - compensationAmount;
        }
    }

    /** 주문 줄 한 줄 */
    public record ItemRow(long id, long participationId, long groupBuyId, Long optionId, int qty, long unitPrice, long itemAmount, int canceledQty, int returnedQty,
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

    private final JdbcTemplate jdbc;

    public ParticipationStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 주문 번호 ───────────────────────────────────────────────────────────

    /** {@code G} + KST 날짜 + 그날 일련 5자리(결정 13 — 넘으면 자리가 는다) */
    public String nextOrderNo(LocalDate day) {
        Integer seq = jdbc.queryForObject("INSERT INTO group_buy_order_counters (day, last_seq) VALUES (?, 1) ON CONFLICT (day) DO UPDATE "
                + "SET last_seq = group_buy_order_counters.last_seq + 1 RETURNING last_seq", Integer.class, Date.valueOf(day));
        return "G" + DAY.format(day) + String.format("%05d", seq);
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    public Optional<ParticipationRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_participations WHERE id = ?", ParticipationStore::row, id).stream().findFirst();
    }

    /** 주문 행을 잠그고 읽는다 — 결제가 PAID 인 흐름(취소를 넣는 흐름)만 주문 행을 먼저 잠근다(결정 37) */
    public Optional<ParticipationRow> lock(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_participations WHERE id = ? FOR UPDATE", ParticipationStore::row, id).stream().findFirst();
    }

    /** 잠겨 있으면 건너뛴다(환불 워커 — 서버 여럿이 나눠 잡는다, 결정 61) */
    public Optional<ParticipationRow> lockSkipLocked(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_participations WHERE id = ? FOR UPDATE SKIP LOCKED", ParticipationStore::row, id).stream().findFirst();
    }

    public List<ItemRow> items(long participationId) {
        return jdbc.query("SELECT id, participation_id, group_buy_id, option_id, qty, unit_price, item_amount, canceled_qty, returned_qty, restocked_qty "
                + "FROM group_buy_participation_items WHERE participation_id = ? ORDER BY option_id NULLS FIRST, id", ParticipationStore::item, participationId);
    }

    /** 그 회원의 그 공구 진행 중 주문(PENDING_PAYMENT~SHIPPED) */
    public Optional<ParticipationRow> inProgress(long groupBuyId, long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_participations WHERE group_buy_id = ? AND user_id = ? AND status IN ("
                + ParticipationStatus.sqlList(ParticipationStatus.IN_PROGRESS) + ")", ParticipationStore::row, groupBuyId, userId).stream().findFirst();
    }

    /**
     * 1인 한도에 쓴 수량 — 그 회원의 Σ(qty − 취소 − 반품)(반품은 처리 방법과 관계없이 뺀다) + 진행 중 선점(결제 중 줄은 취소·반품 0 이라 같은 식). 만료 주문은 세지 않는다(결정 28).
     */
    public int limitUsed(long userId, List<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return 0;
        }
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(groupBuyIds);
        Integer n = jdbc.queryForObject("SELECT coalesce(sum(i.qty - i.canceled_qty - i.returned_qty), 0) FROM group_buy_participation_items i "
                + "JOIN group_buy_participations p ON p.id = i.participation_id WHERE p.user_id = ? AND p.status <> 'EXPIRED' AND p.group_buy_id IN (" + marks(groupBuyIds)
                + ")", Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    /** 선점 시간이 지난 결제 중 주문(선점 만료 워커) */
    public List<Long> overdueHolds(Instant now, int limit) {
        return jdbc.queryForList("SELECT id FROM group_buy_participations WHERE status = 'PENDING_PAYMENT' AND reserved_until < ? ORDER BY reserved_until, id LIMIT ?",
                Long.class, ts(now), limit);
    }

    /** 그 회원의 그 공구 주문 중 선점 시간이 지난 결제 중 주문(조회 때 정리는 본인 주문만 — 결정 85) */
    public List<Long> ownOverdueHolds(long userId, long groupBuyId, Instant now) {
        return jdbc.queryForList("SELECT id FROM group_buy_participations WHERE user_id = ? AND group_buy_id = ? AND status = 'PENDING_PAYMENT' AND reserved_until < ?",
                Long.class, userId, groupBuyId, ts(now));
    }

    /** 불발·중단 공구의 비종결 주문(환불 워커 — 결정 61) */
    public List<Long> unsettledInClosedGroups(int limit) {
        return jdbc.queryForList("SELECT p.id FROM group_buy_participations p JOIN group_buys g ON g.id = p.group_buy_id WHERE g.status IN ('CLOSED_FAILED', 'CANCELED') "
                + "AND p.status IN ('PENDING_PAYMENT', 'JOINED', 'CONFIRMED', 'PREPARING') ORDER BY p.id LIMIT ?", Long.class, limit);
    }

    /** 최근 HOLD_TIMEOUT 만료 횟수(선점 반복 이탈 — 결정 28) */
    public int holdTimeouts(long userId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM participation_status_history WHERE user_id = ? AND to_status = 'EXPIRED' AND cause = 'HOLD_TIMEOUT' "
                + "AND created_at > ?", Integer.class, userId, ts(since));
        return n == null ? 0 : n;
    }

    // ── 쓰기 ────────────────────────────────────────────────────────────────

    public void insert(NewParticipation p) {
        jdbc.update("INSERT INTO group_buy_participations (id, order_no, group_buy_id, user_id, status, items_amount, shipping_fee, amount, cash_amount, group_buy_revision, "
                        + "reserved_until, recipient_enc, phone_enc, postcode, address1_enc, address2_enc, memo_enc, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", p.id(), p.orderNo(), p.groupBuyId(), p.userId(), p.itemsAmount(),
                p.shippingFee(), p.amount(), p.amount(), p.groupBuyRevision(), ts(p.reservedUntil()), p.recipientEnc(), p.phoneEnc(), p.postcode(), p.address1Enc(),
                p.address2Enc(), p.memoEnc(), ts(p.now()), ts(p.now()));
    }

    public void insertItem(long id, long participationId, long groupBuyId, Long optionId, int qty, long unitPrice) {
        jdbc.update("INSERT INTO group_buy_participation_items (id, participation_id, group_buy_id, option_id, qty, unit_price, item_amount) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, participationId, groupBuyId, optionId, qty, unitPrice, unitPrice * qty);
    }

    /** 결제 시도와 선점 끝을 붙인다(결제 중일 때만 — 결정 41) */
    public boolean attachPayment(long id, long paymentOrderId, Instant reservedUntil, Instant now) {
        return jdbc.update("UPDATE group_buy_participations SET payment_order_id = ?, reserved_until = ?, updated_at = ? WHERE id = ? AND status = 'PENDING_PAYMENT'",
                paymentOrderId, ts(reservedUntil), ts(now), id) == 1;
    }

    /** 결제 중 → 만료(원인). {@code dueBefore} 가 있으면 선점 시간이 그 전에 끝났을 때만(이긴 쪽만 true) */
    public boolean expire(long id, ExpireCause cause, Instant dueBefore, Instant now) {
        if (dueBefore == null) {
            return jdbc.update("UPDATE group_buy_participations SET status = 'EXPIRED', expire_cause = ?, expired_at = ?, updated_at = ? WHERE id = ? "
                    + "AND status = 'PENDING_PAYMENT'", cause.name(), ts(now), ts(now), id) == 1;
        }
        return jdbc.update("UPDATE group_buy_participations SET status = 'EXPIRED', expire_cause = ?, expired_at = ?, updated_at = ? WHERE id = ? "
                + "AND status = 'PENDING_PAYMENT' AND reserved_until < ?", cause.name(), ts(now), ts(now), id, ts(dueBefore)) == 1;
    }

    /** 결제 중 → JOINED(마감 후 발주) 또는 CONFIRMED(즉시 발송 — 한 번에) */
    public boolean markPaid(long id, ParticipationStatus to, long paymentOrderId, Instant paidAt, int revision, Instant now) {
        return jdbc.update("UPDATE group_buy_participations SET status = ?, payment_order_id = ?, joined_at = ?, confirmed_at = ?, group_buy_revision = ?, updated_at = ? "
                        + "WHERE id = ? AND status = 'PENDING_PAYMENT'", to.name(), paymentOrderId, ts(paidAt), to == ParticipationStatus.CONFIRMED ? ts(paidAt) : null,
                revision, ts(now), id) == 1;
    }

    /** 결제된 주문의 상태를 바꾼다(지금 상태가 {@code from} 중 하나일 때만) — CANCELED·REFUNDED 면 그 시각도 */
    public boolean transition(long id, Set<ParticipationStatus> from, ParticipationStatus to, Instant now) {
        String at = switch (to) {
            case CANCELED -> ", canceled_at = ?";
            case REFUNDED -> ", refunded_at = ?";
            case CONFIRMED -> ", confirmed_at = ?";
            default -> "";
        };
        List<Object> args = new ArrayList<>();
        args.add(to.name());
        args.add(ts(now));
        if (!at.isEmpty()) {
            args.add(ts(now));
        }
        args.add(id);
        return jdbc.update("UPDATE group_buy_participations SET status = ?, updated_at = ?" + at + " WHERE id = ? AND status IN (" + ParticipationStatus.sqlList(from) + ")",
                args.toArray()) == 1;
    }

    /** 성사 판정 뒤 JOINED → CONFIRMED 일괄(판정 ①과 따로 — 공구 행을 쥐지 않는다). 바뀐 주문 {id, user} */
    public List<long[]> confirmJoinedOfSucceeded(Instant now, int limit) {
        return jdbc.query("UPDATE group_buy_participations p SET status = 'CONFIRMED', confirmed_at = ?, updated_at = ? WHERE p.id IN (SELECT q.id FROM group_buy_participations q "
                        + "JOIN group_buys g ON g.id = q.group_buy_id WHERE q.status = 'JOINED' AND g.status IN ('CLOSED_SUCCESS', 'ORDERED', 'SHIPPING') ORDER BY q.id LIMIT ?) "
                        + "AND p.status = 'JOINED' RETURNING p.id, p.group_buy_id, p.user_id", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, ts(now),
                ts(now), limit);
    }

    /** 줄의 취소 수량을 올린다(아직 가진 수량 안에서만) */
    public boolean cancelItemQty(long itemId, int qty) {
        return jdbc.update("UPDATE group_buy_participation_items SET canceled_qty = canceled_qty + ? WHERE id = ? AND canceled_qty + returned_qty + ? <= qty", qty, itemId,
                qty) == 1;
    }

    /** 환불 누계를 올린다 — 보상까지 합쳐 남은 결제액을 넘지 않을 때만(결정 38 — 공구가 상한의 정본) */
    public boolean addRefunded(long id, long amount, Instant now) {
        return jdbc.update("UPDATE group_buy_participations SET refunded_amount = refunded_amount + ?, updated_at = ? WHERE id = ? "
                + "AND refunded_amount + compensation_amount + ? <= cash_amount", amount, ts(now), id, amount) == 1;
    }

    /** 재부과 배송비 누계(결정 33·42) */
    public void addReshippingCharged(long id, long fee, Instant now) {
        jdbc.update("UPDATE group_buy_participations SET reshipping_fee_charged = reshipping_fee_charged + ?, updated_at = ? WHERE id = ?", fee, ts(now), id);
    }

    public void insertHistory(long id, ParticipationRow p, ParticipationStatus from, ParticipationStatus to, String cause, Long actorId, Instant now) {
        jdbc.update("INSERT INTO participation_status_history (id, participation_id, group_buy_id, user_id, from_status, to_status, cause, actor_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, p.id(), p.groupBuyId(), p.userId(), from == null ? null : from.name(), to.name(), cause, actorId, ts(now));
    }

    public void insertHistory(long id, long participationId, long groupBuyId, long userId, ParticipationStatus from, ParticipationStatus to, String cause, Instant now) {
        jdbc.update("INSERT INTO participation_status_history (id, participation_id, group_buy_id, user_id, from_status, to_status, cause, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, participationId, groupBuyId, userId, from == null ? null : from.name(), to.name(), cause, ts(now));
    }

    static ParticipationRow row(ResultSet rs, int i) throws SQLException {
        String cause = rs.getString("expire_cause");
        return new ParticipationRow(rs.getLong("id"), rs.getString("order_no"), rs.getLong("group_buy_id"), rs.getLong("user_id"),
                ParticipationStatus.valueOf(rs.getString("status")), rs.getLong("items_amount"), rs.getLong("shipping_fee"), rs.getLong("amount"), rs.getLong("point_amount"),
                rs.getLong("cash_amount"), rs.getLong("refunded_amount"), rs.getLong("compensation_amount"), rs.getLong("reshipping_fee_charged"),
                rs.getInt("group_buy_revision"), nLong(rs, "payment_order_id"), instant(rs.getTimestamp("reserved_until")), cause == null ? null : ExpireCause.valueOf(cause),
                instant(rs.getTimestamp("joined_at")), instant(rs.getTimestamp("created_at")));
    }

    static ItemRow item(ResultSet rs, int i) throws SQLException {
        return new ItemRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), nLong(rs, "option_id"), rs.getInt(5), rs.getLong(6), rs.getLong(7), rs.getInt(8), rs.getInt(9),
                rs.getInt(10));
    }
}

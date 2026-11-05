package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonPurchaseStatus;
import com.community.emoticon.entity.EmoticonRefundKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 유료 팩 구매 기록({@code emoticon_purchases}, V142) — 결제 준비 한 번이 한 줄(결제 주문의 {@code order_ref_id}). 지우기·사본 고치기·되돌리기는 DB 트리거가 막는다.
 * Native SQL 인 이유는 {@link EmoticonPackStore} 와 같다(잠금 차례 · 결제 모듈의 JDBC 와 같은 연결 · 스프링 없는 시험). 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class EmoticonPurchaseStore {

    /** 구매 한 줄 */
    public record PurchaseRow(long id, long userId, long packId, Long creatorId, String packName, int amount, EmoticonPurchaseStatus status,
                              Instant refundNoticeAgreedAt, Instant expiresAt, Instant createdAt, Instant closedAt, Long paymentOrderId, Instant paidAt,
                              Instant refundUntil, Instant usedAt, Instant refundedAt, EmoticonRefundKind refundKind, String refundReason, Long refundedBy,
                              Long refundCancelId, Instant refundCompletedAt) {
    }

    /** 새 구매(결제 대기) */
    public record NewPurchase(long id, long userId, long packId, Long creatorId, String packName, int amount, Instant agreedAt, Instant expiresAt, Instant now) {
    }

    /** 목록 줄 — 구매 + 결제 주문의 결제 ID·영수증 + 환불한 운영자 이름(지워진 운영자는 null) + 이 판매가 들어간 정산 달(S4 — 확정 전이면 null) */
    public record ListedRow(PurchaseRow purchase, String paymentId, String receiptUrl, String refundedByName, java.time.LocalDate settlementMonth) {
    }

    /** 관리자 판매 내역 조건 — null 이면 그 거르기 없음(결제 완료 계열만 본다) */
    public record SalesQuery(Instant from, Instant to, Long packId, EmoticonPurchaseStatus status, int offset, int limit) {
    }

    /** 팩 하나의 판매 — 보유 중(환불 뺀) 수·금액 · 결제 완료 계열 전체 수 */
    public record PackSales(int paidCount, long paidAmount, int anyCount) {
    }

    private static final String COLUMNS = "p.id, p.user_id, p.pack_id, p.creator_id, p.pack_name, p.amount, p.status, p.refund_notice_agreed_at, p.expires_at, p.created_at, "
            + "p.closed_at, p.payment_order_id, p.paid_at, p.refund_until, p.used_at, p.refunded_at, p.refund_kind, p.refund_reason, p.refunded_by, p.refund_cancel_id, "
            + "p.refund_completed_at";
    private static final String LISTED = "SELECT " + COLUMNS + ", o.payment_id, o.receipt_url, op.operator_name, p.settlement_month FROM emoticon_purchases p "
            + "JOIN payment_orders o ON o.id = p.payment_order_id LEFT JOIN operators op ON op.id = p.refunded_by";

    private final JdbcTemplate jdbc;

    public EmoticonPurchaseStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(NewPurchase n) {
        jdbc.update("INSERT INTO emoticon_purchases (id, user_id, pack_id, creator_id, pack_name, amount, status, refund_notice_agreed_at, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)", n.id(), n.userId(), n.packId(), n.creatorId(), n.packName(), n.amount(), Timestamp.from(n.agreedAt()),
                Timestamp.from(n.expiresAt()), Timestamp.from(n.now()));
    }

    public Optional<PurchaseRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM emoticon_purchases p WHERE p.id = ?", EmoticonPurchaseStore::rowOf, id).stream().findFirst();
    }

    /** 바꾸기 전에 — 구매 행을 잠근다(결제 확정 · 환불 · 사용함 기록이 같은 구매에서 줄을 선다) */
    public Optional<PurchaseRow> lock(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM emoticon_purchases p WHERE p.id = ? FOR UPDATE", EmoticonPurchaseStore::rowOf, id).stream().findFirst();
    }

    /** 같은 회원·팩의 결제 준비를 줄 세운다(트랜잭션이 끝나면 풀린다 — 결제 확정은 이 잠금을 잡지 않는다) */
    public void lockMemberPack(long userId, long packId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('emoticon-purchase:' || ?::text || ':' || ?::text, 0))", rs -> {
        }, userId, packId);
    }

    /** 회원 이메일(포트원 구매자 칸 — 없으면 빈 글자) */
    public String email(long userId) {
        return jdbc.queryForList("SELECT email FROM users WHERE id = ?", String.class, userId).stream().filter(java.util.Objects::nonNull).findFirst().orElse("");
    }

    /** 그 회원·팩의 결제 대기 구매 */
    public List<Long> pendingIds(long userId, long packId) {
        return jdbc.queryForList("SELECT id FROM emoticon_purchases WHERE user_id = ? AND pack_id = ? AND status = 'PENDING'", Long.class, userId, packId);
    }

    /** 결제 대기를 닫는다(새 준비가 대신했다 · 결제가 거절됐다) — 결제 대기였을 때만 */
    public boolean close(long id, Instant now) {
        return jdbc.update("UPDATE emoticon_purchases SET status = 'CLOSED', closed_at = ? WHERE id = ? AND status = 'PENDING'", Timestamp.from(now), id) == 1;
    }

    /** 결제 완료 — 결제 대기였을 때만(보유는 부르는 쪽이 받기 줄에) */
    public boolean markPaid(long id, long paymentOrderId, Instant paidAt, Instant refundUntil) {
        return jdbc.update("UPDATE emoticon_purchases SET status = 'PAID', payment_order_id = ?, paid_at = ?, refund_until = ? WHERE id = ? AND status = 'PENDING'",
                paymentOrderId, Timestamp.from(paidAt), Timestamp.from(refundUntil), id) == 1;
    }

    /**
     * 「사용함」 — 그 회원이 이 팩들을 산(보유 중인) 구매에 처음 쓴 시각을 찍는다(이미 찍혔으면 그대로). 글·댓글 저장 트랜잭션 안에서 부른다. 찍은 줄 수.
     */
    public int markUsed(long userId, Collection<Long> packIds, Instant now) {
        if (packIds.isEmpty()) {
            return 0;
        }
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(now));
        args.add(userId);
        args.addAll(packIds);
        return jdbc.update("UPDATE emoticon_purchases SET used_at = ? WHERE user_id = ? AND status = 'PAID' AND used_at IS NULL AND pack_id IN ("
                + EmoticonPackStore.marks(packIds.size()) + ")", args.toArray());
    }

    /** 환불 — 보유 중일 때만(취소 대기열 줄 · 종류 · 사유 · 운영자) */
    public boolean markRefunded(long id, EmoticonRefundKind kind, String reason, Long operatorId, long cancelId, Instant now) {
        return jdbc.update("UPDATE emoticon_purchases SET status = 'REFUNDED', refunded_at = ?, refund_kind = ?, refund_reason = ?, refunded_by = ?, refund_cancel_id = ? "
                + "WHERE id = ? AND status = 'PAID'", Timestamp.from(now), kind.name(), reason, operatorId, cancelId, id) == 1;
    }

    /** PG 취소가 끝났다(취소 대기열 줄로 찾는다 — 처음 한 번만) */
    public int completeRefund(long cancelId, Instant now) {
        return jdbc.update("UPDATE emoticon_purchases SET refund_completed_at = ? WHERE refund_cancel_id = ? AND refund_completed_at IS NULL", Timestamp.from(now), cancelId);
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    /** 회원의 구매 내역(결제 완료 계열 — 최근 결제 먼저) */
    public List<ListedRow> mine(long userId) {
        return jdbc.query(LISTED + " WHERE p.user_id = ? AND p.paid_at IS NOT NULL ORDER BY p.paid_at DESC, p.id DESC", EmoticonPurchaseStore::listedOf, userId);
    }

    /** 결제 완료 계열 한 줄(결제 주문 칸 포함) */
    public Optional<ListedRow> listed(long id) {
        return jdbc.query(LISTED + " WHERE p.id = ? AND p.paid_at IS NOT NULL", EmoticonPurchaseStore::listedOf, id).stream().findFirst();
    }

    /** 관리자 판매 내역 한 쪽(최근 결제 먼저) */
    public List<ListedRow> sales(SalesQuery q) {
        List<Object> args = new ArrayList<>();
        String where = salesWhere(q, args);
        args.add(q.limit());
        args.add(q.offset());
        return jdbc.query(LISTED + where + " ORDER BY p.paid_at DESC, p.id DESC LIMIT ? OFFSET ?", EmoticonPurchaseStore::listedOf, args.toArray());
    }

    /** 거른 결과 전체의 수 · 결제 완료(보유 중) 금액 합 */
    public long[] salesTotals(SalesQuery q) {
        List<Object> args = new ArrayList<>();
        String where = salesWhere(q, args);
        return jdbc.queryForObject("SELECT count(*), coalesce(sum(p.amount) FILTER (WHERE p.status = 'PAID'), 0) FROM emoticon_purchases p" + where,
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, args.toArray());
    }

    private static String salesWhere(SalesQuery q, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE p.paid_at IS NOT NULL");
        if (q.from() != null) {
            where.append(" AND p.paid_at >= ?");
            args.add(Timestamp.from(q.from()));
        }
        if (q.to() != null) {
            where.append(" AND p.paid_at < ?");
            args.add(Timestamp.from(q.to()));
        }
        if (q.packId() != null) {
            where.append(" AND p.pack_id = ?");
            args.add(q.packId());
        }
        if (q.status() != null) {
            where.append(" AND p.status = ?");
            args.add(q.status().name());
        }
        return where.toString();
    }

    // ── 집계 ────────────────────────────────────────────────────────────────

    /** 팩마다 판매(결제 완료 계열이 있는 팩만) */
    public Map<Long, PackSales> packSales(Collection<Long> packIds) {
        Map<Long, PackSales> out = new HashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT pack_id, count(*) FILTER (WHERE status = 'PAID'), coalesce(sum(amount) FILTER (WHERE status = 'PAID'), 0), count(*) FROM emoticon_purchases "
                + "WHERE paid_at IS NOT NULL AND pack_id IN (" + EmoticonPackStore.marks(packIds.size()) + ") GROUP BY pack_id", rs -> {
                    out.put(rs.getLong(1), new PackSales(rs.getInt(2), rs.getLong(3), rs.getInt(4)));
                }, packIds.toArray());
        return out;
    }

    /** 작가의 유료 판매액 합계(환불 뺀 — 보유 중인 구매) */
    public long creatorPaidAmount(long creatorId) {
        Long sum = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM emoticon_purchases WHERE creator_id = ? AND status = 'PAID'", Long.class, creatorId);
        return sum == null ? 0 : sum;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    static PurchaseRow rowOf(ResultSet rs, int i) throws SQLException {
        String kind = rs.getString(17);
        return new PurchaseRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), (Long) rs.getObject(4, Long.class), rs.getString(5), rs.getInt(6),
                EmoticonPurchaseStatus.valueOf(rs.getString(7)), EmoticonPackStore.instant(rs, 8), EmoticonPackStore.instant(rs, 9), EmoticonPackStore.instant(rs, 10),
                EmoticonPackStore.instant(rs, 11), (Long) rs.getObject(12, Long.class), EmoticonPackStore.instant(rs, 13), EmoticonPackStore.instant(rs, 14),
                EmoticonPackStore.instant(rs, 15), EmoticonPackStore.instant(rs, 16), kind == null ? null : EmoticonRefundKind.valueOf(kind), rs.getString(18),
                (Long) rs.getObject(19, Long.class), (Long) rs.getObject(20, Long.class), EmoticonPackStore.instant(rs, 21));
    }

    private static ListedRow listedOf(ResultSet rs, int i) throws SQLException {
        java.sql.Date month = rs.getDate(25);
        return new ListedRow(rowOf(rs, i), rs.getString(22), rs.getString(23), rs.getString(24), month == null ? null : month.toLocalDate());
    }
}

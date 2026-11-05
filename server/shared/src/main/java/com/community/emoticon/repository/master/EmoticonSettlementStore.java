package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonSettlementStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 작가 월 정산({@code emoticon_settlements} · {@code emoticon_settlement_events} · 구매 기록의 정산 달 칸, V145). 예정 줄의 금액은 저장하지 않는다 — 계산은
 * {@code EmoticonSettlementCalculator}. 확정 뒤 금액·지급 기록 고치기·지우기는 DB 트리거가 막는다. JDBC 인 이유는 {@link EmoticonPackStore} 와 같다(잠금 차례 · 구매 기록과 같은 연결 ·
 * 스프링 없는 시험). 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class EmoticonSettlementStore {

    /** 확정 때 굳힌 값 */
    public record Frozen(int salesCount, long salesAmount, int refundCount, long refundAmount, long vat, long base, int feeRatePercent, long fee, long share,
                         long carriedIn, long withholding, long payout, long carriedOut, String payeeType, LocalDate payDate, Instant closesAt) {
    }

    /** 정산 줄 하나 — {@code frozen} 은 확정 전이면 null. 처리한 운영자 이름은 지워졌으면 null */
    public record SettlementRow(long creatorId, LocalDate month, EmoticonSettlementStatus status, Frozen frozen, Instant confirmedAt, String confirmedByName,
                                String holdReason, Instant heldAt, String heldByName, Instant paidAt, String paidByName, LocalDate transferDate, String paidMemo,
                                Instant createdAt) {

        /**
         * 굳힌 줄 다음 달로 이어지는 금액 — 굳힌 넘긴 금액 그대로, 다만 포기(WAIVED, V148)한 줄의 양수는 포기한 금액이라 이어지지 않는다(음수 = 받을 돈은 그대로 이어진다).
         * 굳히지 않은 줄이면 0.
         */
        public long carryForward() {
            if (frozen == null) {
                return 0;
            }
            return status == EmoticonSettlementStatus.WAIVED ? Math.min(0, frozen.carriedOut()) : frozen.carriedOut();
        }

        /** 포기한 금액(포기 줄의 양수 넘긴 금액, 아니면 0) */
        public long waivedAmount() {
            return status == EmoticonSettlementStatus.WAIVED && frozen != null ? Math.max(0, frozen.carriedOut()) : 0;
        }
    }

    /** 정산 입력이 되는 구매 한 줄(판매 · 환불) — 정산 달 칸은 이미 들어간 달(없으면 null) */
    public record ActivityRow(long purchaseId, long creatorId, long packId, String packName, int amount, Instant paidAt, Instant refundUntil, Instant refundedAt,
                              LocalDate settlementMonth, LocalDate refundSettlementMonth) {
    }

    /** 처리 기록 한 줄 — {@code amount}·{@code consentedAt} 은 포기(WAIVED) 기록만(V148 — 포기한 금액 · 회원 동의 시각, 운영자 포기 처리는 동의 시각 없음) */
    public record EventRow(String action, Instant at, String byName, String note, Long amount, Instant consentedAt) {
    }

    private static final String COLUMNS = "s.creator_id, s.sales_month, s.status, s.sales_count, s.sales_amount, s.refund_count, s.refund_amount, s.vat_amount, s.base_amount, "
            + "s.fee_rate_percent, s.fee_amount, s.share_amount, s.carried_in, s.withholding_amount, s.payout_amount, s.carried_out, s.payee_type, s.pay_date, s.closes_at, "
            + "s.confirmed_at, oc.operator_name, s.hold_reason, s.held_at, oh.operator_name, s.paid_at, op.operator_name, s.transfer_date, s.paid_memo, s.created_at";
    private static final String SELECT = "SELECT " + COLUMNS + " FROM emoticon_settlements s LEFT JOIN operators oc ON oc.id = s.confirmed_by "
            + "LEFT JOIN operators oh ON oh.id = s.held_by LEFT JOIN operators op ON op.id = s.paid_by";
    private static final String ACTIVITY = "SELECT p.id, p.creator_id, p.pack_id, p.pack_name, p.amount, p.paid_at, p.refund_until, p.refunded_at, p.settlement_month, "
            + "p.refund_settlement_month FROM emoticon_purchases p";
    /** 아직 정산에 들어가지 않은 판매 · 환불(V145 부분 색인 두 개와 같은 조건) */
    private static final String UNSETTLED = "((p.paid_at IS NOT NULL AND p.settlement_month IS NULL AND p.creator_id IS NOT NULL) "
            + "OR (p.refunded_at IS NOT NULL AND p.refund_settlement_month IS NULL AND p.creator_id IS NOT NULL))";

    private final JdbcTemplate jdbc;

    public EmoticonSettlementStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 한 작가의 정산 처리를 줄 세운다(확정 · 보류 · 지급 기록 · 계좌 보기 — 트랜잭션이 끝나면 풀린다) */
    public void lockCreator(long creatorId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('emoticon-settlement:' || ?::text, 0))", rs -> {
        }, creatorId);
    }

    /** 그 가운데 탈퇴한 작가(회원 {@code status = 'WITHDRAWN'} — 파기 배치 R__batch_41 과 같은 판정) — 최소 지급액 없이 지급한다(후속 결정 6) */
    public java.util.Set<Long> withdrawn(Collection<Long> creatorIds) {
        if (creatorIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(jdbc.queryForList("SELECT id FROM users WHERE status = 'WITHDRAWN' AND id IN (" + EmoticonPackStore.marks(creatorIds.size()) + ")",
                Long.class, creatorIds.toArray()));
    }

    // ── 정산 줄 ─────────────────────────────────────────────────────────────

    public Optional<SettlementRow> find(long creatorId, LocalDate month) {
        return jdbc.query(SELECT + " WHERE s.creator_id = ? AND s.sales_month = ?", EmoticonSettlementStore::rowOf, creatorId, Date.valueOf(month)).stream().findFirst();
    }

    /** 한 작가의 모든 줄(오래된 달 먼저) */
    public List<SettlementRow> byCreator(long creatorId) {
        return jdbc.query(SELECT + " WHERE s.creator_id = ? ORDER BY s.sales_month", EmoticonSettlementStore::rowOf, creatorId);
    }

    /** 작가마다 {@code month} 앞의 마지막 굳힌 줄(이월의 시작) */
    public Map<Long, SettlementRow> latestFrozenBefore(LocalDate month) {
        Map<Long, SettlementRow> out = new LinkedHashMap<>();
        jdbc.query("SELECT DISTINCT ON (s.creator_id) " + COLUMNS + " FROM emoticon_settlements s LEFT JOIN operators oc ON oc.id = s.confirmed_by "
                + "LEFT JOIN operators oh ON oh.id = s.held_by LEFT JOIN operators op ON op.id = s.paid_by WHERE s.sales_month < ? AND s.confirmed_at IS NOT NULL "
                + "ORDER BY s.creator_id, s.sales_month DESC", rs -> {
                    SettlementRow r = rowOf(rs, 0);
                    out.put(r.creatorId(), r);
                }, Date.valueOf(month));
        return out;
    }

    /** {@code month} 까지의 굳히지 않은 줄 + {@code month} 의 줄(굳혔어도) — 오래된 달 먼저 */
    public List<SettlementRow> openUpTo(LocalDate month) {
        return jdbc.query(SELECT + " WHERE s.sales_month <= ? AND (s.confirmed_at IS NULL OR s.sales_month = ?) ORDER BY s.creator_id, s.sales_month",
                EmoticonSettlementStore::rowOf, Date.valueOf(month), Date.valueOf(month));
    }

    /** 가장 이른 정산 달(작가 팩 판매가 처음 결제된 달 · 가장 이른 줄) — 없으면 null */
    public LocalDate firstMonth() {
        return jdbc.queryForObject("SELECT least((SELECT date_trunc('month', min(paid_at) AT TIME ZONE 'Asia/Seoul')::date FROM emoticon_purchases "
                + "WHERE paid_at IS NOT NULL AND creator_id IS NOT NULL), (SELECT min(sales_month) FROM emoticon_settlements))", LocalDate.class);
    }

    /** 굳히지 않은(예정) 줄을 만든다 — 이미 있으면 그대로(보류 · 계좌 보기처럼 기록이 생길 때) */
    public void insertIfAbsent(long creatorId, LocalDate month, Instant now) {
        jdbc.update("INSERT INTO emoticon_settlements (creator_id, sales_month, status, created_at, updated_at) VALUES (?, ?, 'ESTIMATED', ?, ?) "
                + "ON CONFLICT (creator_id, sales_month) DO NOTHING", creatorId, Date.valueOf(month), Timestamp.from(now), Timestamp.from(now));
    }

    /** 확정 — 금액을 굳힌다(예정 줄이 있으면 그 줄을, 없으면 새 줄). 예정이었을 때만 */
    public boolean confirm(long creatorId, LocalDate month, Frozen f, long operatorId, Instant now) {
        insertIfAbsent(creatorId, month, now);
        return jdbc.update("UPDATE emoticon_settlements SET status = 'CONFIRMED', sales_count = ?, sales_amount = ?, refund_count = ?, refund_amount = ?, vat_amount = ?, "
                        + "base_amount = ?, fee_rate_percent = ?, fee_amount = ?, share_amount = ?, carried_in = ?, withholding_amount = ?, payout_amount = ?, carried_out = ?, "
                        + "payee_type = ?, pay_date = ?, closes_at = ?, confirmed_at = ?, confirmed_by = ?, updated_at = ? "
                        + "WHERE creator_id = ? AND sales_month = ? AND status = 'ESTIMATED'", f.salesCount(), f.salesAmount(), f.refundCount(), f.refundAmount(), f.vat(),
                f.base(), f.feeRatePercent(), f.fee(), f.share(), f.carriedIn(), f.withholding(), f.payout(), f.carriedOut(), f.payeeType(), Date.valueOf(f.payDate()),
                Timestamp.from(f.closesAt()), Timestamp.from(now), operatorId, Timestamp.from(now), creatorId, Date.valueOf(month)) == 1;
    }

    /** 확정한 줄에 들어간 판매 · 환불에 정산 달을 찍는다(판매 먼저 — 환불 칸은 판매 칸이 있어야 한다) */
    public void markPurchases(long creatorId, LocalDate month, Collection<Long> saleIds, Collection<Long> refundIds) {
        if (!saleIds.isEmpty()) {
            List<Object> args = new ArrayList<>(List.of(Date.valueOf(month), creatorId));
            args.addAll(saleIds);
            jdbc.update("UPDATE emoticon_purchases SET settlement_month = ? WHERE creator_id = ? AND settlement_month IS NULL AND id IN ("
                    + EmoticonPackStore.marks(saleIds.size()) + ")", args.toArray());
        }
        if (!refundIds.isEmpty()) {
            List<Object> args = new ArrayList<>(List.of(Date.valueOf(month), creatorId));
            args.addAll(refundIds);
            jdbc.update("UPDATE emoticon_purchases SET refund_settlement_month = ? WHERE creator_id = ? AND refund_settlement_month IS NULL AND id IN ("
                    + EmoticonPackStore.marks(refundIds.size()) + ")", args.toArray());
        }
    }

    /**
     * 포기(V148) — 굳히지 않은 예정·보류 줄만. 지급액·원천징수 0 으로 굳히고(넘긴 금액 = 작가 몫 + 넘어온 금액 — 부르는 쪽이 그렇게 만든 {@code f}), 보류 칸을 비운다.
     * 줄이 없으면 만든다. {@code operatorId} 는 운영자 포기 처리일 때만(회원 동의로 탈퇴 때 포기하면 null — 시스템).
     */
    public boolean waive(long creatorId, LocalDate month, Frozen f, Long operatorId, Instant now) {
        if (f.payout() != 0 || f.withholding() != 0 || f.carriedOut() != f.share() + f.carriedIn()) {
            throw new IllegalArgumentException("포기한 줄은 지급액·원천징수가 0 이고 넘긴 금액 = 작가 몫 + 넘어온 금액이어야 한다");
        }
        insertIfAbsent(creatorId, month, now);
        return jdbc.update("UPDATE emoticon_settlements SET status = 'WAIVED', sales_count = ?, sales_amount = ?, refund_count = ?, refund_amount = ?, vat_amount = ?, "
                        + "base_amount = ?, fee_rate_percent = ?, fee_amount = ?, share_amount = ?, carried_in = ?, withholding_amount = 0, payout_amount = 0, carried_out = ?, "
                        + "payee_type = ?, pay_date = ?, closes_at = ?, confirmed_at = ?, confirmed_by = ?, hold_reason = NULL, held_at = NULL, held_by = NULL, updated_at = ? "
                        + "WHERE creator_id = ? AND sales_month = ? AND status IN ('ESTIMATED', 'HELD') AND confirmed_at IS NULL", f.salesCount(), f.salesAmount(), f.refundCount(),
                f.refundAmount(), f.vat(), f.base(), f.feeRatePercent(), f.fee(), f.share(), f.carriedIn(), f.carriedOut(), f.payeeType(), Date.valueOf(f.payDate()),
                Timestamp.from(f.closesAt()), Timestamp.from(now), operatorId, Timestamp.from(now), creatorId, Date.valueOf(month)) == 1;
    }

    /** 시스템 보류(작가 탈퇴 — 정산 정보 없음, V148) — 예정(굳히지 않은) 줄만, 줄이 없으면 만든다. 보류한 운영자 없음 */
    public boolean holdBySystem(long creatorId, LocalDate month, String reason, Instant now) {
        insertIfAbsent(creatorId, month, now);
        return jdbc.update("UPDATE emoticon_settlements SET status = 'HELD', hold_reason = ?, held_at = ?, held_by = NULL, updated_at = ? "
                + "WHERE creator_id = ? AND sales_month = ? AND status = 'ESTIMATED' AND confirmed_at IS NULL", reason, Timestamp.from(now), Timestamp.from(now), creatorId,
                Date.valueOf(month)) == 1;
    }

    /** 보류 — 예정·확정일 때만 */
    public boolean hold(long creatorId, LocalDate month, String reason, long operatorId, Instant now) {
        return jdbc.update("UPDATE emoticon_settlements SET status = 'HELD', hold_reason = ?, held_at = ?, held_by = ?, updated_at = ? "
                + "WHERE creator_id = ? AND sales_month = ? AND status IN ('ESTIMATED', 'CONFIRMED')", reason, Timestamp.from(now), operatorId, Timestamp.from(now), creatorId,
                Date.valueOf(month)) == 1;
    }

    /** 보류 풀기 — 보류 전 상태로(굳힌 줄은 확정, 아니면 예정) */
    public boolean unhold(long creatorId, LocalDate month, Instant now) {
        return jdbc.update("UPDATE emoticon_settlements SET status = CASE WHEN confirmed_at IS NULL THEN 'ESTIMATED' ELSE 'CONFIRMED' END, hold_reason = NULL, held_at = NULL, "
                + "held_by = NULL, updated_at = ? WHERE creator_id = ? AND sales_month = ? AND status = 'HELD'", Timestamp.from(now), creatorId, Date.valueOf(month)) == 1;
    }

    /** 지급 완료 기록 — 확정일 때만 */
    public boolean markPaid(long creatorId, LocalDate month, LocalDate transferDate, String memo, long operatorId, Instant now) {
        return jdbc.update("UPDATE emoticon_settlements SET status = 'PAID', paid_at = ?, paid_by = ?, transfer_date = ?, paid_memo = ?, updated_at = ? "
                + "WHERE creator_id = ? AND sales_month = ? AND status = 'CONFIRMED'", Timestamp.from(now), operatorId, Date.valueOf(transferDate), memo, Timestamp.from(now),
                creatorId, Date.valueOf(month)) == 1;
    }

    // ── 처리 기록 ───────────────────────────────────────────────────────────

    public void insertEvent(long id, long creatorId, LocalDate month, String action, Instant at, Long operatorId, String note) {
        jdbc.update("INSERT INTO emoticon_settlement_events (id, creator_id, sales_month, action, at, operator_id, note) VALUES (?, ?, ?, ?, ?, ?, ?)", id, creatorId,
                Date.valueOf(month), action, Timestamp.from(at), operatorId, note);
    }

    /** 포기 기록(V148) — 그 줄에서 포기한 금액 · 회원 동의 시각(운영자 포기 처리면 null) · 사유 */
    public void insertWaivedEvent(long id, long creatorId, LocalDate month, Instant at, Long operatorId, String note, long amount, Instant consentedAt) {
        jdbc.update("INSERT INTO emoticon_settlement_events (id, creator_id, sales_month, action, at, operator_id, note, amount, consented_at) "
                + "VALUES (?, ?, ?, 'WAIVED', ?, ?, ?, ?, ?)", id, creatorId, Date.valueOf(month), Timestamp.from(at), operatorId, note, amount,
                consentedAt == null ? null : Timestamp.from(consentedAt));
    }

    /** 처리 기록(오래된 것 먼저) */
    public List<EventRow> events(long creatorId, LocalDate month) {
        return jdbc.query("SELECT e.action, e.at, o.operator_name, e.note, e.amount, e.consented_at FROM emoticon_settlement_events e "
                        + "LEFT JOIN operators o ON o.id = e.operator_id WHERE e.creator_id = ? AND e.sales_month = ? ORDER BY e.at, e.id",
                (rs, i) -> new EventRow(rs.getString(1), EmoticonPackStore.instant(rs, 2), rs.getString(3), rs.getString(4), rs.getObject(5, Long.class),
                        EmoticonPackStore.instant(rs, 6)), creatorId, Date.valueOf(month));
    }

    // ── 정산 입력(구매 기록) ────────────────────────────────────────────────

    /** 아직 정산에 들어가지 않은 판매·환불 — 모든 작가 */
    public List<ActivityRow> unsettled() {
        return jdbc.query(ACTIVITY + " WHERE " + UNSETTLED, EmoticonSettlementStore::activityOf);
    }

    /** 한 작가의 아직 정산에 들어가지 않은 판매·환불 — {@code lock} 이면 그 구매 행을 잠근다(확정 — 같은 구매의 환불이 확정 계산과 엇갈리지 않게) */
    public List<ActivityRow> unsettled(long creatorId, boolean lock) {
        return jdbc.query(ACTIVITY + " WHERE p.creator_id = ? AND " + UNSETTLED + (lock ? " ORDER BY p.id FOR UPDATE" : ""), EmoticonSettlementStore::activityOf, creatorId);
    }

    /** 굳힌 줄에 들어간 판매·환불(팩별 내역) */
    public List<ActivityRow> settled(long creatorId, LocalDate month) {
        return jdbc.query(ACTIVITY + " WHERE p.creator_id = ? AND (p.settlement_month = ? OR p.refund_settlement_month = ?)", EmoticonSettlementStore::activityOf, creatorId,
                Date.valueOf(month), Date.valueOf(month));
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static SettlementRow rowOf(ResultSet rs, int i) throws SQLException {
        Frozen frozen = null;
        if (rs.getTimestamp(20) != null) {
            frozen = new Frozen(rs.getInt(4), rs.getLong(5), rs.getInt(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getInt(10), rs.getLong(11), rs.getLong(12),
                    rs.getLong(13), rs.getLong(14), rs.getLong(15), rs.getLong(16), rs.getString(17), date(rs, 18), EmoticonPackStore.instant(rs, 19));
        }
        return new SettlementRow(rs.getLong(1), date(rs, 2), EmoticonSettlementStatus.valueOf(rs.getString(3)), frozen, EmoticonPackStore.instant(rs, 20), rs.getString(21),
                rs.getString(22), EmoticonPackStore.instant(rs, 23), rs.getString(24), EmoticonPackStore.instant(rs, 25), rs.getString(26), date(rs, 27), rs.getString(28),
                EmoticonPackStore.instant(rs, 29));
    }

    private static ActivityRow activityOf(ResultSet rs, int i) throws SQLException {
        return new ActivityRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getInt(5), EmoticonPackStore.instant(rs, 6), EmoticonPackStore.instant(rs, 7),
                EmoticonPackStore.instant(rs, 8), date(rs, 9), date(rs, 10));
    }

    private static LocalDate date(ResultSet rs, int i) throws SQLException {
        Date d = rs.getDate(i);
        return d == null ? null : d.toLocalDate();
    }
}

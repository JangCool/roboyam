package com.community.pointshop.repository.master;

import static com.community.pointshop.repository.master.PointShopItemStore.instant;
import static com.community.pointshop.repository.master.PointShopItemStore.marks;
import static com.community.pointshop.repository.master.PointShopItemStore.nullableInt;
import static com.community.pointshop.repository.master.PointShopItemStore.nullableLong;
import static com.community.pointshop.repository.master.PointShopItemStore.ts;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundKind;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.entity.PointShopUsageType;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * 포인트 상점 구매 읽기·쓰기 — {@code com.community.pointshop.service} 만 부른다. JDBC 인 이유는 {@link PointShopItemStore} 와 같다(포인트 사용·사용 취소와 한 트랜잭션,
 * 환불은 구매 행 잠금 → 조건부 UPDATE({@code status = 'COMPLETED'})로 한 번만). 사본 칸은 DB 트리거가 고치지 못하게 막는다.
 */
@Repository
public class PointShopPurchaseStore {

    /** 구매 한 줄 */
    public record PurchaseRow(long id, long userId, long itemId, String itemCode, String itemName, PointShopItemKind itemKind, PointShopUsageType usageType, int price,
                              Integer durationDays, PointShopPurchaseStatus status, Instant refundNoticeAgreedAt, long useLedgerId, Long refundLedgerId,
                              Instant refundedAt, Long refundedByOperatorId, String refundReason, Instant createdAt,
                              PointShopRefundKind refundKind) {
    }

    /** 관리자 구매 내역 한 줄 — 구매 + 회원(닉네임·탈퇴) + 보유 줄의 처음 사용 시각 + 아바타 ID + 예외 환불 운영자 이름 */
    public record AdminRow(PurchaseRow purchase, String nickname, boolean withdrawn, Instant firstUsedAt, String avatarId, String operatorName) {
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

    private static final String COLUMNS = "p.id, p.user_id, p.item_id, p.item_code, p.item_name, p.item_kind, p.usage_type, p.price_point, p.duration_days, p.status, "
            + "p.refund_notice_agreed_at, p.use_ledger_id, p.refund_ledger_id, p.refunded_at, p.refunded_by_operator_id, p.refund_reason, p.created_at, p.refund_kind";
    private static final String REQUEST_COLUMNS = "id, purchase_id, user_item_id, user_id, status, requested_at, decided_at, decided_by_operator_id, reject_reason";

    private final JdbcTemplate jdbc;

    public PointShopPurchaseStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(PurchaseRow p) {
        jdbc.update("INSERT INTO point_shop_purchases (id, user_id, item_id, item_code, item_name, item_kind, usage_type, price_point, duration_days, status, "
                + "refund_notice_agreed_at, use_ledger_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?)", p.id(), p.userId(), p.itemId(),
                p.itemCode(), p.itemName(), p.itemKind().name(), p.usageType().name(), p.price(), p.durationDays(), ts(p.refundNoticeAgreedAt()), p.useLedgerId(),
                ts(p.createdAt()));
    }

    public Optional<PurchaseRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM point_shop_purchases p WHERE p.id = ?", PointShopPurchaseStore::rowOf, id).stream().findFirst();
    }

    /** 환불 전에 — 구매 행을 잠그고 읽는다(셀프 환불·예외 환불이 겹쳐도 한 번만) */
    public Optional<PurchaseRow> findForUpdate(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM point_shop_purchases p WHERE p.id = ? FOR UPDATE", PointShopPurchaseStore::rowOf, id).stream().findFirst();
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
        return jdbc.update("UPDATE point_shop_purchases SET status = 'REFUNDED', refund_ledger_id = ?, refunded_at = ?, refund_kind = ?, refunded_by_operator_id = ?, "
                + "refund_reason = ? WHERE id = ? AND status = 'COMPLETED'", refundLedgerId, ts(now), kind.name(), operatorId, reason, id) == 1;
    }

    // ── 환불 신청(V87) ──────────────────────────────────────────────────────

    public void insertRequest(long id, long purchaseId, long userItemId, long userId, Instant now) {
        jdbc.update("INSERT INTO point_shop_refund_requests (id, purchase_id, user_item_id, user_id, status, requested_at) VALUES (?, ?, ?, ?, 'PENDING', ?)", id,
                purchaseId, userItemId, userId, ts(now));
    }

    public Optional<RequestRow> findRequest(long id) {
        return jdbc.query("SELECT " + REQUEST_COLUMNS + " FROM point_shop_refund_requests WHERE id = ?", PointShopPurchaseStore::requestOf, id).stream().findFirst();
    }

    /** 결정 전에 — 신청 행을 잠그고 읽는다(구매 행을 잠근 뒤에) */
    public Optional<RequestRow> findRequestForUpdate(long id) {
        return jdbc.query("SELECT " + REQUEST_COLUMNS + " FROM point_shop_refund_requests WHERE id = ? FOR UPDATE", PointShopPurchaseStore::requestOf, id).stream()
                .findFirst();
    }

    /** 그 구매의 가장 최근 신청(없으면 빈 값) */
    public Optional<RequestRow> latestRequest(long purchaseId) {
        return jdbc.query("SELECT " + REQUEST_COLUMNS + " FROM point_shop_refund_requests WHERE purchase_id = ? ORDER BY id DESC LIMIT 1", PointShopPurchaseStore::requestOf,
                purchaseId).stream().findFirst();
    }

    /** 구매마다 가장 최근 신청 */
    public Map<Long, RequestRow> latestRequests(Collection<Long> purchaseIds) {
        Map<Long, RequestRow> out = new HashMap<>();
        if (purchaseIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT DISTINCT ON (purchase_id) " + REQUEST_COLUMNS + " FROM point_shop_refund_requests WHERE purchase_id IN (" + marks(purchaseIds.size())
                + ") ORDER BY purchase_id, id DESC", rs -> {
                    RequestRow row = requestOf(rs, 0);
                    out.put(row.purchaseId(), row);
                }, new ArrayList<>(purchaseIds).toArray());
        return out;
    }

    /** 그 구매에 그 상태의 신청이 있나 */
    public boolean hasRequest(long purchaseId, PointShopRefundRequestStatus status) {
        return !jdbc.queryForList("SELECT 1 FROM point_shop_refund_requests WHERE purchase_id = ? AND status = ? LIMIT 1", Integer.class, purchaseId, status.name())
                .isEmpty();
    }

    /** 대기 중인 신청을 결정한다(조건부 — 대기일 때만) */
    public boolean decide(long id, PointShopRefundRequestStatus status, long operatorId, String rejectReason, Instant now) {
        return jdbc.update("UPDATE point_shop_refund_requests SET status = ?, decided_at = ?, decided_by_operator_id = ?, reject_reason = ? WHERE id = ? AND status = 'PENDING'",
                status.name(), ts(now), operatorId, rejectReason, id) == 1;
    }

    /** 관리자 구매 내역 — 최신 구매가 위(ID 내림차순), 쪽마다 {@code size} 줄 */
    public List<AdminRow> adminPage(AdminQuery query) {
        List<Object> args = new ArrayList<>();
        String where = adminWhere(query, args);
        args.add(query.size());
        args.add((long) query.page() * query.size());
        return jdbc.query("SELECT " + COLUMNS + ", pr.nickname, (u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL), ui.first_used_at, i.avatar_id, o.operator_name "
                + ADMIN_FROM + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?", (rs, i) -> new AdminRow(rowOf(rs, i), rs.getString(19), rs.getBoolean(20), instant(rs, 21),
                rs.getString(22), rs.getString(23)), args.toArray());
    }

    public long adminCount(AdminQuery query) {
        List<Object> args = new ArrayList<>();
        String where = adminWhere(query, args);
        Long count = jdbc.queryForObject("SELECT count(*) " + ADMIN_FROM + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private static final String ADMIN_FROM = "FROM point_shop_purchases p LEFT JOIN users u ON u.id = p.user_id LEFT JOIN profiles pr ON pr.user_id = p.user_id "
            + "LEFT JOIN user_items ui ON ui.purchase_id = p.id LEFT JOIN point_shop_items i ON i.id = p.item_id LEFT JOIN operators o ON o.id = p.refunded_by_operator_id ";

    private static String adminWhere(AdminQuery query, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (query.from() != null) {
            where.append(" AND p.created_at >= ?");
            args.add(ts(query.from()));
        }
        if (query.to() != null) {
            where.append(" AND p.created_at < ?");
            args.add(ts(query.to()));
        }
        if (query.status() != null) {
            where.append(" AND p.status = ?");
            args.add(query.status().name());
        }
        if (query.kind() != null) {
            where.append(" AND p.item_kind = ?");
            args.add(query.kind().name());
        }
        if (query.refundRequest() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM point_shop_refund_requests r WHERE r.purchase_id = p.id AND r.status = ?)");
            args.add(query.refundRequest().name());
        }
        String q = query.q() == null ? "" : query.q().strip();
        if (!q.isEmpty()) {
            if (q.matches("\\d{1,19}")) {
                where.append(" AND (p.user_id = ? OR lower(pr.nickname) LIKE ? ESCAPE '\\')");
                args.add(Long.parseLong(q));
            } else {
                where.append(" AND lower(pr.nickname) LIKE ? ESCAPE '\\'");
            }
            args.add("%" + q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        return where.toString();
    }

    private static PurchaseRow rowOf(ResultSet rs, int i) throws SQLException {
        return new PurchaseRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), PointShopItemKind.valueOf(rs.getString(6)),
                PointShopUsageType.valueOf(rs.getString(7)), rs.getInt(8), nullableInt(rs, 9), PointShopPurchaseStatus.valueOf(rs.getString(10)), instant(rs, 11),
                rs.getLong(12), nullableLong(rs, 13), instant(rs, 14), nullableLong(rs, 15), rs.getString(16), instant(rs, 17),
                rs.getString(18) == null ? null : PointShopRefundKind.valueOf(rs.getString(18)));
    }

    private static RequestRow requestOf(ResultSet rs, int i) throws SQLException {
        return new RequestRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), PointShopRefundRequestStatus.valueOf(rs.getString(5)), instant(rs, 6),
                instant(rs, 7), nullableLong(rs, 8), rs.getString(9));
    }
}

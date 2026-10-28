package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.instant;
import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.entity.SupplyChoice;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 취소·반품 요청({@code group_buy_requests})·요청 줄·처리 방법·사진, 단품 공급 불가·회원 선택(결정 53). 상태 전이는 한 문장 조건부 UPDATE(이긴 쪽만).
 */
@Repository
public class GroupBuyRequestStore {

    public record RequestRow(long id, long participationId, long groupBuyId, long userId, RequestType type, RequestStatus status, CollectionMethod method,
                             Resolution resolution, boolean sellerFault, String memo, long previewAmount, Long feePaymentOrderId, long feeAmount, long feePaidAmount,
                             long feeRefundedAmount, Instant closesAt, Instant receivedAt, long compensationAmount, String rejectReasonCode, Instant createdAt,
                             LocalDate defectKnownOn) {

        /** 낸 반품비 중 아직 돌려주지 않은 몫 */
        public long feeHeld() {
            return feePaidAmount - feeRefundedAmount;
        }
    }

    public record ItemRow(long id, long requestId, long participationItemId, long skuId, int qty, RequestReason reason, Long refundAmount, long priorRefundOffset) {
    }

    public record NewRequest(long id, long participationId, long groupBuyId, long userId, RequestType type, RequestStatus status, CollectionMethod method,
                             Resolution resolution, boolean sellerFault, String memo, long previewAmount, long feeAmount, Instant closesAt, LocalDate defectKnownOn,
                             Instant now) {
    }

    public record SupplyFailureRow(long id, long groupBuyId, long skuId, String reason, Instant choiceUntil) {
    }

    public record SupplyChoiceRow(long id, long supplyFailureId, long participationId, long userId, SupplyChoice choice, Instant deadline, boolean defaulted) {
    }

    private static final String COLUMNS = "id, participation_id, group_buy_id, user_id, type, status, collection_method, resolution, seller_fault, memo, preview_amount, "
            + "fee_payment_order_id, fee_amount, fee_paid_amount, fee_refunded_amount, closes_at, received_at, compensation_amount, reject_reason_code, created_at, "
            + "defect_known_on";

    private final JdbcTemplate jdbc;

    public GroupBuyRequestStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 요청 ────────────────────────────────────────────────────────────────

    public void insert(NewRequest r) {
        jdbc.update("INSERT INTO group_buy_requests (id, participation_id, group_buy_id, user_id, type, status, collection_method, resolution, seller_fault, memo, "
                        + "preview_amount, fee_amount, closes_at, defect_known_on, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", r.id(), r.participationId(),
                r.groupBuyId(), r.userId(), r.type().name(), r.status().name(), r.method() == null ? null : r.method().name(), r.resolution().name(), r.sellerFault(), r.memo(),
                r.previewAmount(), r.feeAmount(), ts(r.closesAt()), r.defectKnownOn() == null ? null : java.sql.Date.valueOf(r.defectKnownOn()), ts(r.now()), ts(r.now()));
    }

    public void insertItem(long id, long requestId, long participationItemId, long skuId, int qty, RequestReason reason) {
        jdbc.update("INSERT INTO group_buy_request_items (id, request_id, participation_item_id, sku_id, qty, reason) VALUES (?, ?, ?, ?, ?, ?)", id, requestId,
                participationItemId, skuId, qty, reason.name());
    }

    public Optional<RequestRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_requests WHERE id = ?", GroupBuyRequestStore::row, id).stream().findFirst();
    }

    /** 잠그고 읽는다 — 요청 처리는 주문 행 다음(주문 행 → 요청 행 → 수량 → 결제 행) */
    public Optional<RequestRow> lock(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_requests WHERE id = ? FOR UPDATE", GroupBuyRequestStore::row, id).stream().findFirst();
    }

    public List<RequestRow> openOf(long participationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_requests WHERE participation_id = ? AND status IN (" + RequestStatus.sqlOpen() + ") ORDER BY id",
                GroupBuyRequestStore::row, participationId);
    }

    public boolean hasOpen(long participationId) {
        return !openOf(participationId).isEmpty();
    }

    public List<RequestRow> ofParticipation(long participationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_requests WHERE participation_id = ? ORDER BY id", GroupBuyRequestStore::row, participationId);
    }

    public List<ItemRow> items(long requestId) {
        return jdbc.query("SELECT id, request_id, participation_item_id, sku_id, qty, reason, refund_amount, prior_refund_offset FROM group_buy_request_items WHERE request_id = ? "
                + "ORDER BY sku_id", (rs, i) -> new ItemRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getInt(5), RequestReason.valueOf(rs.getString(6)),
                nLong(rs, "refund_amount"), rs.getLong(8)), requestId);
    }

    /** 상태를 바꾼다(지금 상태가 {@code from} 중 하나일 때만 — 이긴 쪽만 true) */
    public boolean transition(long id, Set<RequestStatus> from, RequestStatus to, Long operatorId, Instant now) {
        String in = from.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(", "));
        String done = to == RequestStatus.COMPLETED ? ", completed_at = ?" : "";
        List<Object> args = new java.util.ArrayList<>(List.of(to.name(), ts(now)));
        if (operatorId != null) {
            args.add(operatorId);
            args.add(ts(now));
        }
        if (!done.isEmpty()) {
            args.add(ts(now));
        }
        args.add(id);
        return jdbc.update("UPDATE group_buy_requests SET status = ?, updated_at = ?" + (operatorId != null ? ", decided_by = ?, decided_at = ?" : "") + done
                + " WHERE id = ? AND status IN (" + in + ")", args.toArray()) == 1;
    }

    public boolean reject(long id, Set<RequestStatus> from, String reasonCode, String note, long operatorId, Instant now) {
        String in = from.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(", "));
        return jdbc.update("UPDATE group_buy_requests SET status = 'REJECTED', reject_reason_code = ?, reject_note = ?, decided_by = ?, decided_at = ?, updated_at = ? "
                + "WHERE id = ? AND status IN (" + in + ")", reasonCode, note, operatorId, ts(now), ts(now), id) == 1;
    }

    public void attachFeePayment(long id, long paymentOrderId, Instant now) {
        jdbc.update("UPDATE group_buy_requests SET fee_payment_order_id = ?, updated_at = ? WHERE id = ?", paymentOrderId, ts(now), id);
    }

    /** 부족분 결제 확정 → 접수(AWAITING_FEE 이고 닫히기 전이고 금액이 같을 때만 — 결정 68) */
    public boolean acceptFee(long id, long paymentOrderId, long amount, Instant now) {
        return jdbc.update("UPDATE group_buy_requests SET status = 'REQUESTED', fee_paid_amount = ?, fee_payment_order_id = ?, updated_at = ? WHERE id = ? "
                + "AND status = 'AWAITING_FEE' AND fee_amount = ? AND closes_at > ?", amount, paymentOrderId, ts(now), id, amount, ts(now)) == 1;
    }

    /** 닫을 때가 된 부족분 결제 전 요청 */
    public List<Long> awaitingFeeDue(Instant now, int limit) {
        return jdbc.queryForList("SELECT id FROM group_buy_requests WHERE status = 'AWAITING_FEE' AND closes_at <= ? ORDER BY closes_at, id LIMIT ?", Long.class, ts(now), limit);
    }

    public void setPickup(long id, String carrierCode, String trackingNo, Instant now) {
        jdbc.update("UPDATE group_buy_requests SET pickup_carrier_code = ?, pickup_tracking_no = ?, updated_at = ? WHERE id = ?", carrierCode, trackingNo, ts(now), id);
    }

    public boolean markReceived(long id, Instant now) {
        return jdbc.update("UPDATE group_buy_requests SET status = 'RECEIVED', received_at = ?, updated_at = ? WHERE id = ? AND status IN ('REQUESTED', 'APPROVED')", ts(now),
                ts(now), id) == 1;
    }

    public void setCompensation(long id, long amount, Instant now) {
        jdbc.update("UPDATE group_buy_requests SET compensation_amount = ?, updated_at = ? WHERE id = ?", amount, ts(now), id);
    }

    public void setPreview(long id, long amount, Instant now) {
        jdbc.update("UPDATE group_buy_requests SET preview_amount = ?, updated_at = ? WHERE id = ?", amount, ts(now), id);
    }

    public boolean toRefund(long id, Instant now) {
        return jdbc.update("UPDATE group_buy_requests SET resolution = 'REFUND', updated_at = ? WHERE id = ? AND resolution = 'EXCHANGE' AND status IN (" + RequestStatus.sqlOpen()
                + ")", ts(now), id) == 1;
    }

    public void setItemAmount(long itemId, long amount, long priorOffset) {
        jdbc.update("UPDATE group_buy_request_items SET refund_amount = ?, prior_refund_offset = ? WHERE id = ?", amount, priorOffset, itemId);
    }

    public void insertDisposition(long id, long requestItemId, String disposition, int qty, Long operatorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_return_dispositions (id, request_item_id, disposition, qty, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)", id, requestItemId,
                disposition, qty, operatorId, ts(now));
    }

    // ── 사진 ────────────────────────────────────────────────────────────────

    public void insertPhoto(long id, String ownerKind, long ownerId, String imageKey, Instant now) {
        jdbc.update("INSERT INTO group_buy_request_photos (id, owner_kind, owner_id, image_key, created_at) VALUES (?, ?, ?, ?, ?)", id, ownerKind, ownerId, imageKey, ts(now));
    }

    /** 그 주인의 붙지 않은 사진을 붙인다 — 붙인 수 */
    public int attachPhotos(long requestId, String ownerKind, long ownerId, List<Long> photoIds, Instant now) {
        int n = 0;
        for (long photoId : photoIds) {
            n += jdbc.update("UPDATE group_buy_request_photos SET request_id = ?, attached_at = ? WHERE id = ? AND owner_kind = ? AND owner_id = ? AND request_id IS NULL",
                    requestId, ts(now), photoId, ownerKind, ownerId);
        }
        return n;
    }

    public int photoCount(long requestId, String ownerKind) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM group_buy_request_photos WHERE request_id = ? AND owner_kind = ?", Integer.class, requestId, ownerKind);
        return n == null ? 0 : n;
    }

    /** 붙지 않은 지 오래된 사진(24시간 — C 195)을 지운다. 지운 이미지 키(저장소 정리는 부르는 쪽) */
    public List<String> deleteLoosePhotos(Instant before) {
        return jdbc.queryForList("DELETE FROM group_buy_request_photos WHERE request_id IS NULL AND created_at < ? RETURNING image_key", String.class, ts(before));
    }

    // ── 단품 공급 불가·회원 선택(결정 53) ─────────────────────────────────────

    public void insertSupplyFailure(long id, long groupBuyId, long skuId, String reason, Instant choiceUntil, Long operatorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_supply_failures (id, group_buy_id, sku_id, reason, choice_until, operator_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id,
                groupBuyId, skuId, reason, ts(choiceUntil), operatorId, ts(now));
    }

    public void insertSupplyChoice(long id, long supplyFailureId, long participationId, long userId, Instant deadline, Instant now) {
        jdbc.update("INSERT INTO group_buy_supply_choices (id, supply_failure_id, participation_id, user_id, deadline, created_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (supply_failure_id, participation_id) DO NOTHING", id, supplyFailureId, participationId, userId, ts(deadline), ts(now));
    }

    /** 답하지 않은 선택(기한 안 — 그동안 발송하지 않는다) */
    public List<SupplyChoiceRow> openChoices(long participationId) {
        return jdbc.query("SELECT id, supply_failure_id, participation_id, user_id, choice, deadline, defaulted FROM group_buy_supply_choices WHERE participation_id = ? "
                + "AND choice IS NULL ORDER BY id", GroupBuyRequestStore::choice, participationId);
    }

    public List<SupplyChoiceRow> choicesOf(long participationId) {
        return jdbc.query("SELECT id, supply_failure_id, participation_id, user_id, choice, deadline, defaulted FROM group_buy_supply_choices WHERE participation_id = ? ORDER BY id",
                GroupBuyRequestStore::choice, participationId);
    }

    /** 회원이 답한다(기한 전, 답하지 않았을 때만) */
    public boolean decideChoice(long id, SupplyChoice choice, boolean defaulted, Instant now) {
        String deadline = defaulted ? " AND deadline <= ?" : " AND deadline > ?";
        return jdbc.update("UPDATE group_buy_supply_choices SET choice = ?, decided_at = ?, defaulted = ? WHERE id = ? AND choice IS NULL" + deadline, choice.name(), ts(now),
                defaulted, id, ts(now)) == 1;
    }

    /** 기한이 지났는데 답하지 않은 선택 */
    public List<Long> choicesDue(Instant now, int limit) {
        return jdbc.queryForList("SELECT id FROM group_buy_supply_choices WHERE choice IS NULL AND deadline <= ? ORDER BY deadline, id LIMIT ?", Long.class, ts(now), limit);
    }

    /** 답을 기다리는 선택이 있는가(발송 막기 — 결정 53) */
    public boolean choicePending(long participationId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM group_buy_supply_choices WHERE participation_id = ? AND choice IS NULL", Integer.class, participationId);
        return n != null && n > 0;
    }

    static RequestRow row(ResultSet rs, int i) throws SQLException {
        String method = rs.getString("collection_method");
        return new RequestRow(rs.getLong("id"), rs.getLong("participation_id"), rs.getLong("group_buy_id"), rs.getLong("user_id"), RequestType.valueOf(rs.getString("type")),
                RequestStatus.valueOf(rs.getString("status")), method == null ? null : CollectionMethod.valueOf(method), Resolution.valueOf(rs.getString("resolution")),
                rs.getBoolean("seller_fault"), rs.getString("memo"), rs.getLong("preview_amount"), nLong(rs, "fee_payment_order_id"), rs.getLong("fee_amount"),
                rs.getLong("fee_paid_amount"), rs.getLong("fee_refunded_amount"), instant(rs.getTimestamp("closes_at")), instant(rs.getTimestamp("received_at")),
                rs.getLong("compensation_amount"), rs.getString("reject_reason_code"), instant(rs.getTimestamp("created_at")),
                rs.getDate("defect_known_on") == null ? null : rs.getDate("defect_known_on").toLocalDate());
    }

    static SupplyChoiceRow choice(ResultSet rs, int i) throws SQLException {
        String choice = rs.getString(5);
        return new SupplyChoiceRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), choice == null ? null : SupplyChoice.valueOf(choice),
                instant(rs.getTimestamp(6)), rs.getBoolean(7));
    }
}

package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.instant;
import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 발송 쪽 표 — 택배사({@code commerce_carriers})·발송({@code group_buy_shipments})·에스크로 호출 대기열({@code group_buy_escrow_jobs})·배송지 열람 기록({@code commerce_address_views}).
 * 상태 바꾸기는 한 문장 조건부 UPDATE(이긴 쪽만).
 */
@Repository
public class FulfillmentStore {

    public record CarrierRow(String code, String name, String trackingUrl, boolean enabled, int sortOrder) {
    }

    public record ShipmentRow(long id, long participationId, long groupBuyId, String kind, Long requestId, String carrierCode, String trackingNo, Instant shippedAt,
                              Instant deliveredAt, Boolean escrowApplicable, Instant escrowLogisticsAt, Instant escrowCompletedAt) {
    }

    public record EscrowJobRow(long id, long shipmentId, String kind, String status, int attempts, Instant nextAttemptAt, String lastError) {
    }

    private static final String SHIPMENT_COLUMNS = "id, participation_id, group_buy_id, kind, request_id, carrier_code, tracking_no, shipped_at, delivered_at, escrow_applicable, "
            + "escrow_logistics_at, escrow_completed_at";
    private static final String JOB_COLUMNS = "id, shipment_id, kind, status, attempts, next_attempt_at, last_error";

    private final JdbcTemplate jdbc;

    public FulfillmentStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 택배사 ──────────────────────────────────────────────────────────────

    public List<CarrierRow> carriers() {
        return jdbc.query("SELECT code, name, tracking_url, enabled, sort_order FROM commerce_carriers ORDER BY sort_order, code",
                (rs, i) -> new CarrierRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5)));
    }

    public void upsertCarrier(CarrierRow c, Long operatorId, Instant now) {
        jdbc.update("INSERT INTO commerce_carriers (code, name, tracking_url, enabled, sort_order, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET name = excluded.name, tracking_url = excluded.tracking_url, enabled = excluded.enabled, sort_order = excluded.sort_order, "
                        + "updated_at = excluded.updated_at, updated_by = excluded.updated_by", c.code(), c.name(), c.trackingUrl(), c.enabled(), c.sortOrder(), ts(now),
                operatorId);
    }

    public void deleteCarrier(String code) {
        jdbc.update("DELETE FROM commerce_carriers WHERE code = ?", code);
    }

    /** 발송·회수 이력에 쓰인 택배사(지우지 못하고 끄기만 — 결정 11) */
    public boolean carrierInUse(String code) {
        Integer n = jdbc.queryForObject("SELECT (SELECT count(*) FROM group_buy_shipments WHERE carrier_code = ?) + (SELECT count(*) FROM group_buy_requests "
                + "WHERE pickup_carrier_code = ?)", Integer.class, code, code);
        return n != null && n > 0;
    }

    // ── 발송 ────────────────────────────────────────────────────────────────

    public void insertShipment(long id, long participationId, long groupBuyId, String kind, Long requestId, String carrierCode, String trackingNo, Long operatorId,
                               Instant now) {
        jdbc.update("INSERT INTO group_buy_shipments (id, participation_id, group_buy_id, kind, request_id, carrier_code, tracking_no, shipped_at, created_by, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, participationId, groupBuyId, kind, requestId, carrierCode, trackingNo, ts(now), operatorId,
                ts(now), ts(now));
    }

    public Optional<ShipmentRow> original(long participationId) {
        return jdbc.query("SELECT " + SHIPMENT_COLUMNS + " FROM group_buy_shipments WHERE participation_id = ? AND kind = 'ORIGINAL'", FulfillmentStore::shipment,
                participationId).stream().findFirst();
    }

    public Optional<ShipmentRow> shipment(long id) {
        return jdbc.query("SELECT " + SHIPMENT_COLUMNS + " FROM group_buy_shipments WHERE id = ?", FulfillmentStore::shipment, id).stream().findFirst();
    }

    public List<ShipmentRow> shipmentsOf(long participationId) {
        return jdbc.query("SELECT " + SHIPMENT_COLUMNS + " FROM group_buy_shipments WHERE participation_id = ? ORDER BY shipped_at, id", FulfillmentStore::shipment,
                participationId);
    }

    public boolean correctTracking(long shipmentId, String carrierCode, String trackingNo, Instant now) {
        return jdbc.update("UPDATE group_buy_shipments SET carrier_code = ?, tracking_no = ?, updated_at = ? WHERE id = ?", carrierCode, trackingNo, ts(now), shipmentId) == 1;
    }

    public void markDelivered(long participationId, Instant now) {
        jdbc.update("UPDATE group_buy_shipments SET delivered_at = ?, updated_at = ? WHERE participation_id = ? AND kind = 'ORIGINAL' AND delivered_at IS NULL", ts(now),
                ts(now), participationId);
    }

    /** 에스크로 결과 기록(조건부 — 결정 64) */
    public void stampEscrow(long shipmentId, String kind, boolean applicable, Instant now) {
        if (!applicable) {
            jdbc.update("UPDATE group_buy_shipments SET escrow_applicable = false, updated_at = ? WHERE id = ?", ts(now), shipmentId);
        } else if ("LOGISTICS".equals(kind)) {
            jdbc.update("UPDATE group_buy_shipments SET escrow_applicable = true, escrow_logistics_at = coalesce(escrow_logistics_at, ?), updated_at = ? WHERE id = ?", ts(now),
                    ts(now), shipmentId);
        } else {
            jdbc.update("UPDATE group_buy_shipments SET escrow_applicable = true, escrow_completed_at = ?, updated_at = ? WHERE id = ? AND escrow_logistics_at IS NOT NULL "
                    + "AND escrow_completed_at IS NULL", ts(now), ts(now), shipmentId);
        }
    }

    /**
     * 구매 확정 대상(결정 14) — 배송완료 뒤 {@code deliveredBefore} 이전이고, 배송 정보가 등록됐고, 주문에 열린 요청이 없는 처음 발송.
     */
    public List<Long> escrowCompleteDue(Instant deliveredBefore, int limit) {
        return jdbc.queryForList("SELECT s.id FROM group_buy_shipments s WHERE s.kind = 'ORIGINAL' AND s.delivered_at < ? AND s.escrow_logistics_at IS NOT NULL "
                + "AND s.escrow_completed_at IS NULL AND s.escrow_applicable IS NOT false AND NOT EXISTS (SELECT 1 FROM group_buy_requests r WHERE r.participation_id = s.participation_id "
                + "AND r.status IN ('AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED')) AND NOT EXISTS (SELECT 1 FROM group_buy_escrow_jobs j WHERE j.shipment_id = s.id "
                + "AND j.kind = 'COMPLETE' AND j.status IN ('PENDING', 'GAVE_UP')) ORDER BY s.delivered_at, s.id LIMIT ?", Long.class, ts(deliveredBefore), limit);
    }

    // ── 에스크로 호출 대기열(결정 64) ───────────────────────────────────────

    /** 넣는다 — 같은 발송·종류의 끝나지 않은 일이 있으면 그 줄을 지금 다시 시도하게(송장 정정은 새 값을 그 줄이 보낸다) */
    public void enqueueEscrow(long id, long shipmentId, String kind, Instant now) {
        int updated = jdbc.update("UPDATE group_buy_escrow_jobs SET next_attempt_at = ? WHERE shipment_id = ? AND kind = ? AND status = 'PENDING'", ts(now), shipmentId, kind);
        if (updated == 0) {
            jdbc.update("INSERT INTO group_buy_escrow_jobs (id, shipment_id, kind, next_attempt_at, created_at) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (shipment_id, kind) WHERE status = 'PENDING' DO NOTHING", id, shipmentId, kind, ts(now), ts(now));
        }
    }

    public List<Long> escrowDue(Instant now, int limit) {
        return jdbc.queryForList("SELECT id FROM group_buy_escrow_jobs WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY next_attempt_at, id LIMIT ?", Long.class,
                ts(now), limit);
    }

    /** 집는다 — 잠겨 있지 않고 차례가 됐으면 다음 시도를 {@code leaseUntil} 로 미뤄 다른 서버가 겹쳐 부르지 않게(호출은 트랜잭션 밖) */
    public Optional<EscrowJobRow> claimEscrow(long id, Instant now, Instant leaseUntil) {
        return jdbc.query("UPDATE group_buy_escrow_jobs SET next_attempt_at = ?, attempts = attempts + 1 WHERE id = (SELECT id FROM group_buy_escrow_jobs WHERE id = ? "
                + "AND status = 'PENDING' AND next_attempt_at <= ? FOR UPDATE SKIP LOCKED) RETURNING " + JOB_COLUMNS, FulfillmentStore::job, ts(leaseUntil), id, ts(now)).stream()
                .findFirst();
    }

    public void finishEscrow(long id, String status, String error, Instant now) {
        jdbc.update("UPDATE group_buy_escrow_jobs SET status = ?, last_error = ?, completed_at = ? WHERE id = ? AND status = 'PENDING'", status, error, ts(now), id);
    }

    public void retryEscrow(long id, String error, Instant nextAttemptAt) {
        jdbc.update("UPDATE group_buy_escrow_jobs SET last_error = ?, next_attempt_at = ? WHERE id = ? AND status = 'PENDING'", error, ts(nextAttemptAt), id);
    }

    public List<EscrowJobRow> escrowJobs(long shipmentId) {
        return jdbc.query("SELECT " + JOB_COLUMNS + " FROM group_buy_escrow_jobs WHERE shipment_id = ? ORDER BY created_at, id", FulfillmentStore::job, shipmentId);
    }

    // ── 배송지 열람 기록 ───────────────────────────────────────────────────

    public void insertAddressView(long id, String kind, Long participationId, long groupBuyId, long operatorId, String reason, Integer rowCount, Instant now) {
        jdbc.update("INSERT INTO commerce_address_views (id, kind, participation_id, group_buy_id, operator_id, reason, row_count, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, kind, participationId, groupBuyId, operatorId, reason, rowCount, ts(now));
    }

    static ShipmentRow shipment(ResultSet rs, int i) throws SQLException {
        Object applicable = rs.getObject("escrow_applicable");
        return new ShipmentRow(rs.getLong("id"), rs.getLong("participation_id"), rs.getLong("group_buy_id"), rs.getString("kind"), nLong(rs, "request_id"),
                rs.getString("carrier_code"), rs.getString("tracking_no"), instant(rs.getTimestamp("shipped_at")), instant(rs.getTimestamp("delivered_at")),
                applicable == null ? null : (Boolean) applicable, instant(rs.getTimestamp("escrow_logistics_at")), instant(rs.getTimestamp("escrow_completed_at")));
    }

    static EscrowJobRow job(ResultSet rs, int i) throws SQLException {
        return new EscrowJobRow(rs.getLong("id"), rs.getLong("shipment_id"), rs.getString("kind"), rs.getString("status"), rs.getInt("attempts"),
                instant(rs.getTimestamp("next_attempt_at")), rs.getString("last_error"));
    }
}

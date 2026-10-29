package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyOrderAdminView;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.payment.dto.response.PaymentMemberView;
import com.community.payment.service.PaymentDetailText;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 관리자 공구 주문(admin-web {@code useGroupBuyOrderApi}) — 찾기·상세·배송지 열람·메모·발주/준비중/발송/배송완료 일괄·송장 정정·운영 환불·배송 명단 내보내기.
 * 업무는 shared {@link FulfillmentService}·{@link AddressRevealService}·{@link GroupBuyAdminRefundService}. 배송지는 암호문이라 찾지 못한다(10-9).
 * 상세 열람은 감사 {@code GROUPBUY_ORDER_VIEW}(회원 번호·닉네임 — 개인정보 열람). 배송 명단은 한 번 쓰는 내려받기 주소({@code data:} — 파일을 서버에 남기지 않는다, 가정).
 */
@Service
public class GroupBuyOrderAdminFacade {

    private final JdbcTemplate jdbc;
    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyRequestStore requests;
    private final GroupBuyRefundStore refunds;
    private final FulfillmentStore fulfillmentStore;
    private final FulfillmentService fulfillment;
    private final AddressRevealService addresses;
    private final GroupBuyAdminRefundService adminRefunds;
    private final GroupBuyShipDeadlines shipDeadlines;
    private final GroupBuyAudit audit;
    private final PaymentDetailText paymentText;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyOrderAdminFacade(DataSource dataSource, ParticipationStore participations, GroupBuyStore groups, GroupBuyRequestStore requests, GroupBuyRefundStore refunds,
                                    FulfillmentStore fulfillmentStore, FulfillmentService fulfillment, AddressRevealService addresses,
                                    GroupBuyAdminRefundService adminRefunds, GroupBuyShipDeadlines shipDeadlines, GroupBuyAudit audit, PaymentDetailText paymentText,
                                    ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.participations = participations;
        this.groups = groups;
        this.requests = requests;
        this.refunds = refunds;
        this.fulfillmentStore = fulfillmentStore;
        this.fulfillment = fulfillment;
        this.addresses = addresses;
        this.adminRefunds = adminRefunds;
        this.shipDeadlines = shipDeadlines;
        this.paymentText = paymentText;
        this.audit = audit;
        this.clocks = clocks;
    }

    // ── 목록·상세 ───────────────────────────────────────────────────────────

    /** 주문 번호·닉네임·회원 번호로 찾기, 날짜는 한국 날짜 양 끝 포함(결정 22) */
    public GroupBuyOrderAdminView.ListResponse list(String status, String groupBuyId, String alert, String q, LocalDate from, LocalDate to, int page, int size) {
        int s = Math.min(200, Math.max(1, size));
        int p = Math.max(0, page);
        StringBuilder where = new StringBuilder(" FROM group_buy_participations pa LEFT JOIN profiles pr ON pr.user_id = pa.user_id WHERE pa.status <> 'EXPIRED'");
        List<Object> args = new ArrayList<>();
        if (groupBuyId != null && !groupBuyId.isBlank()) {
            where.append(" AND pa.group_buy_id = ?");
            args.add(AdminIds.field(groupBuyId, "groupBuyId"));
        }
        if (q != null && !q.isBlank()) {
            String v = q.strip();
            where.append(" AND (pa.order_no = ? OR pr.nickname ILIKE ? OR pa.user_id::text = ?)");
            args.add(v);
            args.add("%" + v.replace("%", "\\%").replace("_", "\\_") + "%");
            args.add(v);
        }
        if (from != null) {
            where.append(" AND pa.created_at >= ?");
            args.add(Timestamp.from(from.atStartOfDay(ServiceTime.ZONE).toInstant()));
        }
        if (to != null) {
            where.append(" AND pa.created_at < ?");
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant()));
        }
        Instant now = now();
        if ("SHIP_OVERDUE".equals(alert)) {
            List<Long> ids = shipDeadlines.overdue(now, 10_000).stream().map(GroupBuyShipDeadlines.Due::participationId).toList();
            where.append(" AND pa.id = ANY (?::bigint[])");
            args.add(ids.toArray(new Long[0]));
        } else if ("DELIVERY_STALE".equals(alert)) {
            where.append(" AND pa.status = 'SHIPPED' AND pa.shipped_at < ?");
            args.add(Timestamp.from(now.minus(Duration.ofDays(CommerceGroupBuySettingsFacade.DELIVERY_STALE_DAYS))));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ParticipationStatus st : ParticipationStatus.values()) {
            counts.put(st.name(), 0L);
        }
        jdbc.query("SELECT pa.status, count(*)" + where + " GROUP BY pa.status", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        }, args.toArray());
        if (status != null && !status.isBlank()) {
            where.append(" AND pa.status = ?");
            args.add(AdminIds.value(ParticipationStatus.class, status, "status").name());
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        args.add(s);
        args.add(p * s);
        List<Long> ids = jdbc.queryForList("SELECT pa.id" + where + " ORDER BY pa.created_at DESC, pa.id DESC LIMIT ? OFFSET ?", Long.class, args.toArray());
        List<GroupBuyOrderAdminView.Row> rows = new ArrayList<>();
        for (long id : ids) {
            rows.add(row(participations.find(id).orElseThrow()));
        }
        return new GroupBuyOrderAdminView.ListResponse(rows, total == null ? 0 : total, counts);
    }

    public GroupBuyOrderAdminView.Detail detail(AdminActor actor, String idText) {
        long id = AdminIds.parse(idText, GroupBuyException::orderNotFound);
        ParticipationRow o = participations.find(id).orElseThrow(GroupBuyException::orderNotFound);
        audit.record(actor.operatorId(), AuditActions.GROUPBUY_ORDER_VIEW, GroupBuyAudit.TARGET_ORDER, id, actor.ip(), Map.of("orderNo", o.orderNo()));
        return detail(actor, o);
    }

    private GroupBuyOrderAdminView.Detail detail(AdminActor actor, ParticipationRow o) {
        GroupBuyOrderAdminView.Row r = row(o);
        GroupBuyRow g = groups.find(o.groupBuyId()).orElseThrow();
        Map<Long, GroupBuyStore.SkuRow> skus = new HashMap<>();
        groups.skus(g.id()).forEach(s -> skus.put(s.id(), s));
        List<ItemRow> lines = participations.items(o.id());
        List<GroupBuyOrderAdminView.Item> items = lines.stream().map(i -> new GroupBuyOrderAdminView.Item(Long.toString(i.id()), Long.toString(i.skuId()),
                skus.containsKey(i.skuId()) ? skus.get(i.skuId()).name() : "", i.main() ? "MAIN" : "ADDON", i.qty(), i.unitPrice(), i.itemAmount(), i.canceledQty(),
                i.returnedQty(), i.restockedQty())).toList();
        GroupBuyOrderAdminView.Amounts amounts = new GroupBuyOrderAdminView.Amounts(o.itemsAmount(), o.shippingFee(), o.amount(), o.cashAmount(), o.pointAmount(),
                o.refundedAmount(), o.compensationAmount(), o.reshippingFeeCharged(), o.remainingCash());
        GroupBuyOrderAdminView.Payment payment = o.paymentOrderId() == null ? null : jdbc.query("SELECT po.id, po.payment_id, po.status, po.pay_method, po.paid_at, "
                + "EXISTS (SELECT 1 FROM group_buy_shipments s WHERE s.participation_id = ? AND s.escrow_applicable) FROM payment_orders po WHERE po.id = ?",
                (rs, i) -> new GroupBuyOrderAdminView.Payment(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3), paymentText.payMethod(rs.getString(4)), rs.getBoolean(6),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()), o.id(), o.paymentOrderId()).stream().findFirst().orElse(null);
        GroupBuyOrderAdminView.MaskedAddress address;
        if (o.addressMaskedAt() != null) {
            address = new GroupBuyOrderAdminView.MaskedAddress("", "", "", "", "", o.addressMaskedAt());
        } else {
            ShippingAddress m = addresses.masked(o.id());
            address = new GroupBuyOrderAdminView.MaskedAddress(nz(m.recipient()), nz(m.phone()), nz(m.postcode()), (nz(m.address1()) + " " + nz(m.address2())).strip(),
                    nz(m.memo()), null);
        }
        List<GroupBuyOrderAdminView.AddressView> views = jdbc.query("SELECT o.operator_name, v.reason, v.created_at FROM commerce_address_views v "
                + "LEFT JOIN operators o ON o.id = v.operator_id WHERE v.participation_id = ? AND v.kind = 'REVEAL' ORDER BY v.created_at DESC",
                (rs, i) -> new GroupBuyOrderAdminView.AddressView(nz(rs.getString(1)), rs.getString(2), rs.getTimestamp(3).toInstant()), o.id());
        String memo = jdbc.query("SELECT admin_memo FROM group_buy_participations WHERE id = ?", (rs, i) -> rs.getString(1), o.id()).stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        List<GroupBuyOrderAdminView.StatusChange> history = jdbc.query("SELECT h.from_status, h.to_status, o.operator_name, h.cause, h.created_at, h.actor_id "
                + "FROM participation_status_history h LEFT JOIN operators o ON o.id = h.actor_id WHERE h.participation_id = ? ORDER BY h.created_at, h.id",
                (rs, i) -> new GroupBuyOrderAdminView.StatusChange(rs.getString(1), rs.getString(2), rs.getString(3) != null ? rs.getString(3)
                        : rs.getObject(6) == null ? "SYSTEM" : "MEMBER", rs.getString(4), rs.getTimestamp(5).toInstant()), o.id());
        List<GroupBuyOrderAdminView.RefundRecord> refundRows = new ArrayList<>();
        for (GroupBuyRefundStore.RefundRow rf : refunds.ofParticipation(o.id())) {
            Map<String, Object> extra = jdbc.queryForList("SELECT r.reason, op.operator_name FROM group_buy_refunds r LEFT JOIN operators op ON op.id = r.actor_id WHERE r.id = ?",
                    rf.id()).stream().findFirst().orElse(Map.of());
            refundRows.add(new GroupBuyOrderAdminView.RefundRecord(Long.toString(rf.id()), rf.cause().name(), rf.method(), refundState(rf), rf.itemRefund(),
                    rf.shippingRefund(), rf.returnFee(), rf.reshippingFee(), rf.reshippingRefund(), rf.compensationPg() + rf.compensationBank(), rf.amount(),
                    rf.cause() == com.community.groupbuy.entity.RefundCause.SUPPLY_FAILURE || rf.cause() == com.community.groupbuy.entity.RefundCause.DELAY,
                    (String) extra.get("reason"), (String) extra.get("operator_name"), rf.requestId() == null ? null : Long.toString(rf.requestId()), rf.createdAt(),
                    rf.completedAt()));
        }
        jdbc.query("SELECT p.id, p.amount, p.reason, p.created_at, o.operator_name, p.seller_fault FROM group_buy_admin_refunds p LEFT JOIN operators o "
                + "ON o.id = p.requested_by WHERE p.participation_id = ? AND p.status = 'AWAITING_APPROVAL'", rs -> {
            refundRows.add(new GroupBuyOrderAdminView.RefundRecord("pending-" + rs.getLong(1), "ADMIN", "PG_CANCEL", "AWAITING_APPROVAL", rs.getLong(2), 0, 0, 0, 0, 0,
                    rs.getLong(2), rs.getBoolean(6), rs.getString(3), rs.getString(5), null, rs.getTimestamp(4).toInstant(), null));
        }, o.id());
        List<GroupBuyOrderAdminView.Shipment> shipments = fulfillmentStore.shipmentsOf(o.id()).stream().map(this::shipment).toList();
        List<GroupBuyRequestStore.RequestRow> reqs = requests.ofParticipation(o.id());
        List<GroupBuyOrderAdminView.RequestRef> requestRefs = reqs.stream().map(q -> new GroupBuyOrderAdminView.RequestRef(Long.toString(q.id()), q.type().name(),
                q.status().name(), q.createdAt())).toList();
        GroupBuyOrderAdminView.SupplyChoice supply = requests.choicesOf(o.id()).stream().reduce((a, b) -> b).map(c -> new GroupBuyOrderAdminView.SupplyChoice(
                c.choice() == null ? null : c.choice().name(), c.deadline(), c.defaulted())).orElse(null);
        long threshold = groups.settings().largeRefundThreshold();
        boolean open = reqs.stream().anyMatch(q -> RequestStatus.OPEN.contains(q.status()));
        boolean preShip = ParticipationStatus.PAID_BEFORE_SHIPPING.contains(o.status());
        String blocked = o.status() == ParticipationStatus.SHIPPED ? "SHIPPED" : open ? "OPEN_REQUEST"
                : !preShip && o.status() != ParticipationStatus.DELIVERED ? "CLOSED" : refunds.paymentUnreconciled(o.paymentOrderId()) ? "PAYMENT_UNRECONCILED" : null;
        boolean canRefund = actor.root() || actor.can(Permission.COMMERCE_REFUND_APPROVE);
        boolean canManage = actor.root() || actor.can(Permission.COMMERCE_ORDER_MANAGE);
        GroupBuyOrderAdminView.Actions actions = new GroupBuyOrderAdminView.Actions(canManage && o.status() == ParticipationStatus.CONFIRMED,
                canManage && (o.status() == ParticipationStatus.PREPARING || (g.immediate() && o.status() == ParticipationStatus.CONFIRMED)) && !open,
                canManage && o.status() == ParticipationStatus.SHIPPED, canManage && o.status() == ParticipationStatus.SHIPPED,
                o.addressMaskedAt() == null && (actor.root() || actor.can(Permission.COMMERCE_ADDRESS_REVEAL)),
                !canRefund || blocked != null ? null : preShip ? "LINES" : "AMOUNT", blocked);
        return new GroupBuyOrderAdminView.Detail(r.id(), r.orderNo(), r.groupBuy(), r.member(), r.itemSummary(), r.totalQty(), r.amount(), r.refundedAmount(), r.status(),
                r.openRequest(), r.supplyChoicePending(), r.delayCancelable(), r.carrierCode(), r.trackingNo(), r.createdAt(), r.paidAt(), r.shippedAt(), r.deliveredAt(),
                items, amounts, payment, address, views, nz(memo), history, refundRows, shipments, requestRefs, supply, shipDeadlines.dueAt(o.id()),
                refunds.operatorAmountTotal(o.id()), threshold, actions);
    }

    // ── 배송지·메모 ─────────────────────────────────────────────────────────

    public GroupBuyOrderAdminView.RevealedAddress reveal(AdminActor actor, String idText, GroupBuyAdminRequest.Reason body) {
        long id = AdminIds.parse(idText, GroupBuyException::orderNotFound);
        AddressRevealService.Revealed r = addresses.reveal(id, body == null ? null : body.reason(), actor.operatorId(), actor.ip());
        ShippingAddress a = r.address();
        return new GroupBuyOrderAdminView.RevealedAddress(a.recipient(), a.phone(), a.postcode(), a.address1(), nz(a.address2()), nz(a.memo()), r.hideAt());
    }

    public GroupBuyOrderAdminView.Detail memo(AdminActor actor, String idText, GroupBuyAdminRequest.Memo body) {
        long id = AdminIds.parse(idText, GroupBuyException::orderNotFound);
        String memo = body == null || body.memo() == null ? "" : body.memo().strip();
        if (memo.length() > 2000) {
            throw ValidationException.of("memo", new MessageRef("groupbuy.order.memo.tooLong", 2000));
        }
        if (jdbc.update("UPDATE group_buy_participations SET admin_memo = ?, updated_at = ? WHERE id = ?", memo.isEmpty() ? null : memo, Timestamp.from(now()), id) == 0) {
            throw GroupBuyException.orderNotFound();
        }
        audit.record(actor.operatorId(), AuditActions.GROUPBUY_ORDER_MEMO, GroupBuyAudit.TARGET_ORDER, id, actor.ip(), Map.of("length", memo.length()));
        return detail(actor, participations.find(id).orElseThrow());
    }

    // ── 발송 ────────────────────────────────────────────────────────────────

    public List<GroupBuyOrderAdminView.CarrierChoice> carriers() {
        return fulfillment.carriers().stream().filter(FulfillmentStore.CarrierRow::enabled).map(c -> new GroupBuyOrderAdminView.CarrierChoice(c.code(), c.name())).toList();
    }

    public GroupBuyOrderAdminView.Bulk prepare(AdminActor actor, GroupBuyAdminRequest.OrderIds body) {
        return bulk(fulfillment.prepare(ids(body), actor.operatorId(), actor.ip()));
    }

    public GroupBuyOrderAdminView.Bulk deliver(AdminActor actor, GroupBuyAdminRequest.OrderIds body) {
        return bulk(fulfillment.deliver(ids(body), actor.operatorId(), actor.ip()));
    }

    public GroupBuyOrderAdminView.Bulk ship(AdminActor actor, GroupBuyAdminRequest.Ship body) {
        List<FulfillmentService.ShipLine> lines = new ArrayList<>();
        if (body != null && body.rows() != null) {
            for (GroupBuyAdminRequest.ShipRow r : body.rows()) {
                lines.add(new FulfillmentService.ShipLine(AdminIds.field(r.orderId(), "rows"), r.carrierCode(), r.trackingNo()));
            }
        }
        return bulk(fulfillment.ship(lines, actor.operatorId(), actor.ip()));
    }

    public GroupBuyOrderAdminView.Detail tracking(AdminActor actor, String idText, GroupBuyAdminRequest.Tracking body) {
        long id = AdminIds.parse(idText, GroupBuyException::orderNotFound);
        fulfillment.correctTracking(id, body == null ? null : body.carrierCode(), body == null ? null : body.trackingNo(), actor.operatorId(), actor.ip());
        return detail(actor, participations.find(id).orElseThrow());
    }

    // ── 운영 환불 ───────────────────────────────────────────────────────────

    public GroupBuyOrderAdminView.RefundPreview refundPreview(AdminActor actor, String idText, GroupBuyAdminRequest.Refund body) {
        long id = AdminIds.parse(idText, GroupBuyException::orderNotFound);
        GroupBuyAdminRefundService.Preview p = adminRefunds.preview(id, lines(id, body), body == null ? null : body.amount(),
                body != null && Boolean.TRUE.equals(body.businessFault()), large(actor));
        return new GroupBuyOrderAdminView.RefundPreview(p.itemRefund(), p.shippingRefund(), p.reshippingFee(), p.total(), p.remainingAfter(), p.adminTotalAfter(),
                p.threshold(), p.needsLarge());
    }

    public GroupBuyOrderAdminView.RefundResult refund(AdminActor actor, String idText, GroupBuyAdminRequest.Refund body) {
        long id = AdminIds.parse(idText, GroupBuyException::orderNotFound);
        GroupBuyAdminRefundService.Result r = adminRefunds.refund(id, lines(id, body), body == null ? null : body.amount(),
                body != null && Boolean.TRUE.equals(body.businessFault()), body == null ? null : body.reason(), large(actor), actor.operatorId(), actor.ip());
        return new GroupBuyOrderAdminView.RefundResult(r.state(), detail(actor, participations.find(id).orElseThrow()));
    }

    /** 승인 대기 승인({@code approve})·거절({@code reject}) — 대기 줄이 이 주문 것이어야 한다 */
    public GroupBuyOrderAdminView.RefundResult decidePending(AdminActor actor, String idText, String pendingText, String decision) {
        long id = AdminIds.parse(idText, GroupBuyException::orderNotFound);
        long pendingId = AdminIds.parse(pendingText.startsWith("pending-") ? pendingText.substring(8) : pendingText, GroupBuyException::requestNotFound);
        Long owner = jdbc.query("SELECT participation_id FROM group_buy_admin_refunds WHERE id = ?", (rs, i) -> rs.getLong(1), pendingId).stream().findFirst().orElse(null);
        if (owner == null || owner != id) {
            throw GroupBuyException.requestNotFound();
        }
        String state;
        if ("approve".equals(decision)) {
            state = adminRefunds.approvePending(pendingId, actor.operatorId(), actor.ip()).state();
        } else if ("reject".equals(decision)) {
            adminRefunds.rejectPending(pendingId, actor.operatorId());
            state = "REJECTED";
        } else {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
        return new GroupBuyOrderAdminView.RefundResult(state, detail(actor, participations.find(id).orElseThrow()));
    }

    // ── 배송 명단 ───────────────────────────────────────────────────────────

    /** {@code commerce.export} + {@code commerce.address.reveal} 둘 다(10-9) — 컨트롤러가 하나, 여기서 다른 하나 */
    public GroupBuyOrderAdminView.ExportResult export(AdminActor actor, GroupBuyAdminRequest.Export body) {
        if (!actor.root() && !(actor.can(Permission.COMMERCE_EXPORT) && actor.can(Permission.COMMERCE_ADDRESS_REVEAL))) {
            throw new CommonException(ErrorCode.FORBIDDEN);
        }
        long groupBuyId = AdminIds.field(body == null ? null : body.groupBuyId(), "groupBuyId");
        AddressRevealService.ExportFile file = addresses.export(groupBuyId, body.reason(), actor.operatorId(), actor.ip());
        String url = "data:text/csv;charset=utf-8;base64," + Base64.getEncoder().encodeToString(file.csv().getBytes(StandardCharsets.UTF_8));
        return new GroupBuyOrderAdminView.ExportResult(file.fileName(), file.rowCount(), url);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    GroupBuyOrderAdminView.Row row(ParticipationRow o) {
        GroupBuyRow g = groups.find(o.groupBuyId()).orElseThrow();
        Map<String, Object> m = jdbc.queryForList("SELECT pr.nickname, (u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL) AS gone FROM "
                + "(SELECT ?::bigint AS id) x LEFT JOIN users u ON u.id = x.id LEFT JOIN profiles pr ON pr.user_id = x.id", o.userId()).get(0);
        PaymentMemberView member = GroupBuyAdminFacade.member(o.userId(), (String) m.get("nickname"), Boolean.TRUE.equals(m.get("gone")));
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT s.name, i.qty FROM group_buy_participation_items i JOIN group_buy_skus s ON s.id = i.sku_id "
                + "WHERE i.participation_id = ? ORDER BY s.kind DESC, s.sort_order, i.id", o.id());
        String summary = lines.isEmpty() ? "" : lines.get(0).get("name") + " × " + lines.get(0).get("qty") + (lines.size() > 1 ? " 외 " + (lines.size() - 1) : "");
        int totalQty = lines.stream().mapToInt(l -> ((Number) l.get("qty")).intValue()).sum();
        Optional<GroupBuyRequestStore.RequestRow> open = requests.openOf(o.id()).stream().findFirst();
        Optional<java.time.Instant> delay = groups.firstDelayNoticeAt(g.id());
        boolean delayCancelable = delay.isPresent() && ParticipationStatus.PAID_BEFORE_SHIPPING.contains(o.status()) && o.joinedAt() != null
                && !o.joinedAt().isAfter(delay.get());
        Optional<FulfillmentStore.ShipmentRow> ship = fulfillmentStore.original(o.id());
        return new GroupBuyOrderAdminView.Row(Long.toString(o.id()), o.orderNo(), new GroupBuyOrderAdminView.GroupBuyRef(Long.toString(g.id()),
                GroupBuyAdminFacade.GroupBuyMemberLikeNames.title(g), g.roundNo(), g.status().name(), g.fulfillment()), member, summary, totalQty, o.amount(),
                o.refundedAmount(), o.status().name(), open.map(q -> new GroupBuyOrderAdminView.OpenRequest(Long.toString(q.id()), q.type().name())).orElse(null),
                requests.choicePending(o.id()), delayCancelable, ship.map(FulfillmentStore.ShipmentRow::carrierCode).orElse(null),
                ship.map(FulfillmentStore.ShipmentRow::trackingNo).orElse(null), o.createdAt(), o.joinedAt(), o.shippedAt(), o.deliveredAt());
    }

    private GroupBuyOrderAdminView.Shipment shipment(FulfillmentStore.ShipmentRow s) {
        FulfillmentStore.CarrierRow c = fulfillment.carriers().stream().filter(x -> x.code().equals(s.carrierCode())).findFirst().orElse(null);
        return new GroupBuyOrderAdminView.Shipment(s.kind(), s.carrierCode(), c == null ? s.carrierCode() : c.name(), s.trackingNo(),
                c == null ? null : c.trackingUrl().replace("{no}", s.trackingNo()), s.shippedAt(), s.deliveredAt());
    }

    private static String refundState(GroupBuyRefundStore.RefundRow r) {
        return switch (r.status()) {
            case IN_PROGRESS -> "PENDING";
            case COMPLETED -> "DONE";
            case DELAYED -> "DELAYED";
            case MANUAL -> "MANUAL";
        };
    }

    private GroupBuyOrderAdminView.Bulk bulk(FulfillmentService.BatchResult r) {
        List<GroupBuyOrderAdminView.BulkRow> rows = r.results().stream().map(x -> new GroupBuyOrderAdminView.BulkRow(Long.toString(x.participationId()),
                participations.find(x.participationId()).map(ParticipationRow::orderNo).orElse(""), x.ok(), x.code(), null)).toList();
        return new GroupBuyOrderAdminView.Bulk(r.succeeded(), r.failed(), rows);
    }

    private static List<Long> ids(GroupBuyAdminRequest.OrderIds body) {
        List<Long> out = new ArrayList<>();
        if (body != null && body.orderIds() != null) {
            for (String id : body.orderIds()) {
                out.add(AdminIds.field(id, "orderIds"));
            }
        }
        return out;
    }

    /** 화면의 줄은 주문 줄 ID — 서비스는 단품 ID 로 받는다(주문 안에서 단품 줄은 하나) */
    private List<OrderLine> lines(long participationId, GroupBuyAdminRequest.Refund body) {
        if (body == null || body.lines() == null) {
            return null;
        }
        Map<Long, Long> itemToSku = new HashMap<>();
        participations.items(participationId).forEach(i -> itemToSku.put(i.id(), i.skuId()));
        List<OrderLine> out = new ArrayList<>();
        for (GroupBuyAdminRequest.RefundLine l : body.lines()) {
            Long sku = itemToSku.get(AdminIds.field(l.itemId(), "lines"));
            if (sku == null || l.qty() == null) {
                throw ValidationException.of("lines", new MessageRef("groupbuy.cancel.lines.invalid"));
            }
            out.add(new OrderLine(sku, l.qty()));
        }
        return out;
    }

    private static boolean large(AdminActor actor) {
        return actor.root() || actor.can(Permission.COMMERCE_REFUND_LARGE);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }

    /** 주문 목록이 보지 않는 상태 */
    static final Set<ParticipationStatus> HIDDEN = Set.of(ParticipationStatus.EXPIRED);
}

package com.community.groupbuy.service;

import com.community.commerce.image.GroupBuyImageUploader;
import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyOrderAdminView;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.RejectReason;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.entity.ReturnDisposition;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore.RequestRow;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.media.service.ImageStorage;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.time.ServiceTime;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 취소·반품 요청 대기열(admin-web {@code useGroupBuyRequestApi}) — 업무는 shared {@link GroupBuyRequestService}(2b-3). 환불 계산 미리 보기는 요청 화면과 같은
 * {@code GroupBuyRefundCalculator}(결정 72): 지금 다시 계산한 값이 요청 때 값({@code preview_amount})과 다르면 {@code recalculated} 를 함께 준다.
 * 경과 영업일·기한 단계(P35)는 {@link GroupBuyBusinessDays}(취소는 요청한 날, 반품은 돌려받은 날부터).
 */
@Service
public class GroupBuyRequestAdminFacade {

    private final JdbcTemplate jdbc;
    private final GroupBuyRequestStore requests;
    private final GroupBuyRefundStore refunds;
    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyRequestService service;
    private final GroupBuyOrderAdminFacade orders;
    private final GroupBuyImageUploader uploader;
    private final ImageStorage storage;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyRequestAdminFacade(DataSource dataSource, GroupBuyRequestStore requests, GroupBuyRefundStore refunds, ParticipationStore participations,
                                      GroupBuyStore groups, GroupBuyRequestService service, GroupBuyOrderAdminFacade orders, GroupBuyImageUploader uploader,
                                      ImageStorage storage, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.requests = requests;
        this.refunds = refunds;
        this.participations = participations;
        this.groups = groups;
        this.service = service;
        this.orders = orders;
        this.uploader = uploader;
        this.storage = storage;
        this.clocks = clocks;
    }

    public GroupBuyOrderAdminView.RequestList list(String status, String type, String level, String q, int page, int size) {
        int s = Math.min(100, Math.max(1, size));
        int p = Math.max(1, page);
        StringBuilder where = new StringBuilder(" FROM group_buy_requests r JOIN group_buy_participations pa ON pa.id = r.participation_id "
                + "LEFT JOIN profiles pr ON pr.user_id = r.user_id WHERE r.status <> 'AWAITING_FEE'");
        List<Object> args = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            where.append(" AND r.type = ?");
            args.add(AdminIds.value(RequestType.class, type, "type").name());
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (pa.order_no = ? OR pr.nickname ILIKE ? OR r.user_id::text = ?)");
            args.add(q.strip());
            args.add("%" + q.strip().replace("%", "\\%").replace("_", "\\_") + "%");
            args.add(q.strip());
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String st : List.of("REQUESTED", "APPROVED", "COMPLETED", "REJECTED", "WITHDRAWN", "SUPERSEDED")) {
            counts.put(st, 0L);
        }
        jdbc.query("SELECT CASE WHEN r.status = 'RECEIVED' THEN 'APPROVED' ELSE r.status END, count(*)" + where + " GROUP BY 1", rs -> {
            counts.merge(rs.getString(1), rs.getLong(2), Long::sum);
        }, args.toArray());
        if (status != null && !status.isBlank()) {
            if ("APPROVED".equals(status)) {
                where.append(" AND r.status IN ('APPROVED', 'RECEIVED')");
            } else {
                where.append(" AND r.status = ?");
                args.add(AdminIds.value(RequestStatus.class, status, "status").name());
            }
        }
        List<Long> ids = jdbc.queryForList("SELECT r.id" + where + " ORDER BY r.created_at, r.id", Long.class, args.toArray());
        List<GroupBuyOrderAdminView.RequestRow> rows = new ArrayList<>();
        for (long id : ids) {
            rows.add(row(requests.find(id).orElseThrow()));
        }
        if ("WARN".equals(level)) {
            rows = rows.stream().filter(r -> !"OK".equals(r.deadlineLevel())).toList();
        }
        long total = rows.size();
        List<GroupBuyOrderAdminView.RequestRow> pageRows = rows.stream().skip((long) (p - 1) * s).limit(s).toList();
        return new GroupBuyOrderAdminView.RequestList(pageRows, total, counts);
    }

    public GroupBuyOrderAdminView.RequestDetail detail(AdminActor actor, String idText) {
        RequestRow r = requests.find(AdminIds.parse(idText, GroupBuyException::requestNotFound)).orElseThrow(GroupBuyException::requestNotFound);
        GroupBuyOrderAdminView.RequestRow row = row(r);
        ParticipationRow o = participations.find(r.participationId()).orElseThrow();
        GroupBuyRow g = groups.find(r.groupBuyId()).orElseThrow();
        Map<Long, GroupBuyStore.SkuRow> skus = new HashMap<>();
        groups.skus(g.id()).forEach(s -> skus.put(s.id(), s));
        List<GroupBuyRequestStore.ItemRow> lines = requests.items(r.id());
        Map<Long, List<GroupBuyOrderAdminView.DispositionQty>> dispositions = new HashMap<>();
        jdbc.query("SELECT d.request_item_id, d.disposition, d.qty FROM group_buy_return_dispositions d JOIN group_buy_request_items i ON i.id = d.request_item_id "
                + "WHERE i.request_id = ?", rs -> {
            dispositions.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new GroupBuyOrderAdminView.DispositionQty(rs.getString(2), rs.getInt(3)));
        }, r.id());
        String memo = r.memo() == null ? "" : r.memo();
        List<GroupBuyOrderAdminView.RequestItem> items = lines.stream().map(i -> {
            GroupBuyStore.SkuRow sku = skus.get(i.skuId());
            return new GroupBuyOrderAdminView.RequestItem(Long.toString(i.id()), Long.toString(i.participationItemId()), sku == null ? "" : sku.name(),
                    sku == null || sku.main() ? "MAIN" : "ADDON", i.qty(), i.reason() == null ? "CHANGE_OF_MIND" : i.reason().name(), memo,
                    i.refundAmount() == null ? 0 : i.refundAmount(), dispositions.getOrDefault(i.id(), List.of()));
        }).toList();
        List<GroupBuyOrderAdminView.Photo> memberPhotos = photos(r.id(), "MEMBER");
        List<GroupBuyOrderAdminView.Photo> operatorPhotos = photos(r.id(), "OPERATOR");
        Map<String, Object> extra = jdbc.queryForMap("SELECT r.pickup_carrier_code, r.pickup_tracking_no, r.reject_note, o.operator_name, "
                + "r.decided_at, r.completed_at FROM group_buy_requests r LEFT JOIN operators o ON o.id = r.decided_by WHERE r.id = ?", r.id());
        GroupBuyOrderAdminView.Breakdown current = breakdown(r, lines);
        GroupBuyOrderAdminView.Recalculated recalculated = current.total() != r.previewAmount() && RequestStatus.OPEN.contains(r.status())
                ? new GroupBuyOrderAdminView.Recalculated(current, List.of("RECALCULATED")) : null;
        GroupBuyOrderAdminView.ExchangeShipment exchange = jdbc.query("SELECT carrier_code, tracking_no, shipped_at FROM group_buy_shipments WHERE request_id = ? AND kind = 'EXCHANGE'",
                (rs, i) -> new GroupBuyOrderAdminView.ExchangeShipment(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()), r.id()).stream().findFirst()
                .orElse(null);
        List<GroupBuyOrderAdminView.HistoryLine> history = new ArrayList<>();
        history.add(new GroupBuyOrderAdminView.HistoryLine("REQUESTED", "MEMBER", null, r.createdAt()));
        if (extra.get("decided_at") instanceof Timestamp t) {
            history.add(new GroupBuyOrderAdminView.HistoryLine(r.status() == RequestStatus.REJECTED ? "REJECTED" : "APPROVED", nz((String) extra.get("operator_name")),
                    (String) extra.get("reject_note"), t.toInstant()));
        }
        if (r.receivedAt() != null) {
            history.add(new GroupBuyOrderAdminView.HistoryLine("APPROVED", nz((String) extra.get("operator_name")), "RECEIVED", r.receivedAt()));
        }
        if (extra.get("completed_at") instanceof Timestamp t) {
            history.add(new GroupBuyOrderAdminView.HistoryLine(screenStatus(r.status()), nz((String) extra.get("operator_name")), null, t.toInstant()));
        }
        boolean open = RequestStatus.OPEN.contains(r.status());
        boolean manage = actor.root() || actor.can(Permission.COMMERCE_ORDER_MANAGE);
        boolean approve = actor.root() || actor.can(Permission.COMMERCE_REFUND_APPROVE);
        boolean exchangeMode = r.resolution() == Resolution.EXCHANGE;
        boolean resell = g.status() == GroupBuyStatus.RECRUITING && lines.stream().anyMatch(i -> i.reason() == RequestReason.CHANGE_OF_MIND);
        GroupBuyOrderAdminView.RequestActions actions = new GroupBuyOrderAdminView.RequestActions(approve && r.status() == RequestStatus.REQUESTED,
                manage && (r.status() == RequestStatus.REQUESTED || (r.type() == RequestType.RETURN && EnumSet.of(RequestStatus.APPROVED, RequestStatus.RECEIVED)
                        .contains(r.status()))),
                manage && r.type() == RequestType.RETURN && r.status() == RequestStatus.APPROVED && r.method() == CollectionMethod.COMPANY_PICKUP,
                manage && r.type() == RequestType.RETURN && r.status() == RequestStatus.APPROVED,
                manage && approve && r.type() == RequestType.RETURN && r.status() == RequestStatus.RECEIVED && !exchangeMode,
                manage && exchangeMode && EnumSet.of(RequestStatus.APPROVED, RequestStatus.RECEIVED).contains(r.status()), manage && exchangeMode && open, resell);
        String productRestriction = jdbc.queryForObject("SELECT p.withdrawal_restriction FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE g.id = ?",
                String.class, g.id());
        return new GroupBuyOrderAdminView.RequestDetail(row.id(), row.type(), row.status(), row.resolution(), row.order(), row.groupBuy(), row.member(), row.lineCount(),
                row.totalQty(), row.mainReason(), row.collectionMethod(), row.previewAmount(), row.createdAt(), row.receivedAt(), row.elapsedBusinessDays(),
                row.deadlineLevel(), row.extraPayment(), items, memberPhotos, extra.get("pickup_tracking_no") == null ? null
                : new GroupBuyOrderAdminView.Pickup((String) extra.get("pickup_carrier_code"), (String) extra.get("pickup_tracking_no")), current, recalculated,
                r.compensationAmount(), groups.settings().compensationCap(), productRestriction != null && !productRestriction.isBlank(), r.rejectReasonCode(),
                (String) extra.get("reject_note"), operatorPhotos, exchange, refunds.operatorAmountTotal(o.id()), groups.settings().largeRefundThreshold(),
                (String) extra.get("operator_name"), extra.get("completed_at") instanceof Timestamp t ? t.toInstant() : extra.get("decided_at") instanceof Timestamp d
                ? d.toInstant() : null, history, actions);
    }

    // ── 처리 ────────────────────────────────────────────────────────────────

    public GroupBuyOrderAdminView.RequestDetail approve(AdminActor actor, String idText, GroupBuyAdminRequest.Approve body) {
        service.approve(id(idText), body == null ? null : body.expectedAmount(), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    public GroupBuyOrderAdminView.RequestDetail reject(AdminActor actor, String idText, GroupBuyAdminRequest.Reject body) {
        GroupBuyAdminRequest.Reject b = body == null ? new GroupBuyAdminRequest.Reject(null, null, null) : body;
        List<Long> photos = new ArrayList<>();
        if (b.photoIds() != null) {
            for (String p : b.photoIds()) {
                photos.add(AdminIds.field(p, "photoIds"));
            }
        }
        service.reject(id(idText), AdminIds.value(RejectReason.class, b.reasonCode(), "reasonCode"), b.reason(), photos, null, null, actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    public GroupBuyOrderAdminView.Photo photo(AdminActor actor, MultipartFile file) {
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        GroupBuyImageUploader.Uploaded up = uploader.upload("evidence", data);
        long id = service.registerOperatorPhoto(actor.operatorId(), up.key());
        return new GroupBuyOrderAdminView.Photo(Long.toString(id), up.url());
    }

    public GroupBuyOrderAdminView.RequestDetail pickup(AdminActor actor, String idText, GroupBuyAdminRequest.Pickup body) {
        service.recordPickup(id(idText), body == null ? null : body.carrierCode(), body == null ? null : body.trackingNo(), actor.operatorId());
        return detail(actor, idText);
    }

    /** 회수 확인 — 반환받은 날은 처리한 시각(화면이 보낸 날은 쓰지 않는다 — 3영업일 기한이 늦춰지지 않게, 가정) */
    public GroupBuyOrderAdminView.RequestDetail receive(AdminActor actor, String idText) {
        service.receive(id(idText), actor.operatorId());
        return detail(actor, idText);
    }

    public GroupBuyOrderAdminView.RequestDetail complete(AdminActor actor, String idText, GroupBuyAdminRequest.Complete body) {
        long id = id(idText);
        GroupBuyAdminRequest.Complete b = body == null ? new GroupBuyAdminRequest.Complete(null, null, null) : body;
        service.complete(id, dispositions(id, b.dispositions()), b.compensationAmount() == null ? 0 : b.compensationAmount(), b.expectedAmount(),
                actor.root() || actor.can(Permission.COMMERCE_REFUND_LARGE), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    /** 교환 재발송 — 돌려받은 물건은 화면이 처리 방법을 보내지 않으면 줄마다 「공급처 반송」(하자·오배송 물건 — 가정) */
    public GroupBuyOrderAdminView.RequestDetail exchangeShip(AdminActor actor, String idText, GroupBuyAdminRequest.ExchangeShip body) {
        long id = id(idText);
        List<GroupBuyRequestService.DispositionInput> plan = requests.items(id).stream()
                .map(i -> new GroupBuyRequestService.DispositionInput(i.skuId(), ReturnDisposition.RETURN_TO_SUPPLIER, i.qty())).toList();
        service.exchangeShip(id, body == null ? null : body.carrierCode(), body == null ? null : body.trackingNo(), plan, 0,
                actor.root() || actor.can(Permission.COMMERCE_REFUND_LARGE), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    public GroupBuyOrderAdminView.RequestDetail convertToRefund(AdminActor actor, String idText) {
        service.convertToRefund(id(idText), actor.operatorId());
        return detail(actor, idText);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private GroupBuyOrderAdminView.RequestRow row(RequestRow r) {
        ParticipationRow o = participations.find(r.participationId()).orElseThrow();
        GroupBuyOrderAdminView.Row order = orders.row(o);
        List<GroupBuyRequestStore.ItemRow> lines = requests.items(r.id());
        Instant now = clocks.getIfUnique(Clock::systemUTC).instant();
        Instant start = r.type() == RequestType.CANCEL ? r.createdAt() : r.receivedAt();
        List<LocalDate> holidays = groups.settings().holidays();
        int elapsed = start == null ? 0 : GroupBuyBusinessDays.elapsed(ServiceTime.dateOf(start), ServiceTime.dateOf(now), holidays);
        GroupBuyBusinessDays.Level level = start == null || !RequestStatus.OPEN.contains(r.status()) ? GroupBuyBusinessDays.Level.NONE
                : GroupBuyBusinessDays.deadlineLevel(start, now, holidays);
        GroupBuyOrderAdminView.ExtraPayment extra = r.feePaymentOrderId() == null ? null : jdbc.query("SELECT status, amount, payment_id, paid_at FROM payment_orders WHERE id = ?",
                (rs, i) -> new GroupBuyOrderAdminView.ExtraPayment(feeStatus(rs.getString(1), r), rs.getLong(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(), r.feeRefundedAmount()), r.feePaymentOrderId()).stream().findFirst().orElse(null);
        return new GroupBuyOrderAdminView.RequestRow(Long.toString(r.id()), r.type().name(), screenStatus(r.status()), r.resolution() == null ? "REFUND" : r.resolution().name(),
                new GroupBuyOrderAdminView.OrderRef(Long.toString(o.id()), o.orderNo()), order.groupBuy(), order.member(), lines.size(),
                lines.stream().mapToInt(GroupBuyRequestStore.ItemRow::qty).sum(), lines.isEmpty() || lines.get(0).reason() == null ? "CHANGE_OF_MIND"
                : lines.get(0).reason().name(), r.method() == null ? null : r.method().name(), r.previewAmount(), r.createdAt(), r.receivedAt(), elapsed,
                switch (level) {
                    case NONE -> "OK";
                    case WARNING -> "WARN";
                    case CRITICAL -> "CRITICAL";
                }, extra);
    }

    /** 대기열 화면의 상태 — 회수 확인(RECEIVED)은 승인 묶음, 닫힘(EXPIRED)은 철회 묶음으로 보인다 */
    private static String screenStatus(RequestStatus s) {
        return switch (s) {
            case RECEIVED -> "APPROVED";
            case EXPIRED -> "WITHDRAWN";
            case AWAITING_FEE -> "REQUESTED";
            default -> s.name();
        };
    }

    private static String feeStatus(String paymentStatus, RequestRow r) {
        if (r.feeRefundedAmount() > 0) {
            return r.feeRefundedAmount() >= r.feePaidAmount() ? "REFUNDED" : "PARTIAL_REFUNDED";
        }
        return switch (paymentStatus) {
            case "PAID" -> "PAID";
            case "READY" -> "READY";
            default -> "EXPIRED";
        };
    }

    private GroupBuyOrderAdminView.Breakdown breakdown(RequestRow r, List<GroupBuyRequestStore.ItemRow> lines) {
        try {
            List<GroupBuyRequestService.LineInput> in = lines.stream().map(i -> new GroupBuyRequestService.LineInput(i.skuId(), i.qty(), i.reason())).toList();
            GroupBuyRequestService.Preview p = service.preview(r.userId(), r.participationId(), r.type(), in);
            GroupBuyRefundCalculator.Result c = r.type() == RequestType.CANCEL ? p.cancel()
                    : p.byMethod().get(r.method() == null ? CollectionMethod.COMPANY_PICKUP : r.method());
            return new GroupBuyOrderAdminView.Breakdown(c.itemRefund(), c.shippingRefund(), c.returnFee(), c.reshippingFee(), c.reshippingRefund(), r.compensationAmount(),
                    c.total(), c.sellerFault());
        } catch (RuntimeException e) {
            // 끝난 요청(이미 반품·환불돼 지금 다시 계산할 수 없다) — 남긴 환불 기록으로 보인다
            long item = 0;
            long ship = 0;
            long fee = 0;
            long reship = 0;
            long reshipRefund = 0;
            long total = 0;
            for (GroupBuyRefundStore.RefundRow f : refunds.ofRequest(r.id())) {
                item += f.itemRefund();
                ship += f.shippingRefund();
                fee += f.returnFee();
                reship += f.reshippingFee();
                reshipRefund += f.reshippingRefund();
                total += f.amount();
            }
            return new GroupBuyOrderAdminView.Breakdown(item, ship, fee, reship, reshipRefund, r.compensationAmount(), total == 0 ? r.previewAmount() : total,
                    r.sellerFault());
        }
    }

    private List<GroupBuyOrderAdminView.Photo> photos(long requestId, String owner) {
        return jdbc.query("SELECT id, image_key FROM group_buy_request_photos WHERE request_id = ? AND owner_kind = ? ORDER BY id",
                (rs, i) -> new GroupBuyOrderAdminView.Photo(Long.toString(rs.getLong(1)), storage.publicUrl(rs.getString(2))), requestId, owner);
    }

    /** 화면의 처리 방법 줄은 요청 줄 ID — 서비스는 단품 ID 로 받는다 */
    private List<GroupBuyRequestService.DispositionInput> dispositions(long requestId, List<GroupBuyAdminRequest.Disposition> in) {
        Map<Long, Long> itemToSku = new HashMap<>();
        requests.items(requestId).forEach(i -> itemToSku.put(i.id(), i.skuId()));
        List<GroupBuyRequestService.DispositionInput> out = new ArrayList<>();
        if (in != null) {
            for (GroupBuyAdminRequest.Disposition d : in) {
                Long sku = itemToSku.get(AdminIds.field(d.requestItemId(), "dispositions"));
                if (sku == null) {
                    throw com.community.zconfiguration.exception.ValidationException.of("dispositions",
                            new com.community.zconfiguration.message.MessageRef("groupbuy.value.invalid"));
                }
                out.add(new GroupBuyRequestService.DispositionInput(sku, AdminIds.value(ReturnDisposition.class, d.disposition(), "dispositions"),
                        d.qty() == null ? 0 : d.qty()));
            }
        }
        return out;
    }

    private static long id(String text) {
        return AdminIds.parse(text, GroupBuyException::requestNotFound);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

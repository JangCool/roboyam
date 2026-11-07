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
import com.community.groupbuy.repository.master.GroupBuyOrderAdminQueries;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRowNames;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

/**
 * 관리자 공구 주문(admin-web {@code useGroupBuyOrderApi}) — 찾기·상세·배송지 열람·메모·발주/준비중/발송/배송완료 일괄·송장 정정·운영 환불·배송 명단 내보내기.
 * 업무는 shared {@link FulfillmentService}·{@link AddressRevealService}·{@link GroupBuyAdminRefundService}. 배송지는 암호문이라 찾지 못한다(10-9).
 * 상세 열람은 감사 {@code GROUPBUY_ORDER_VIEW}(회원 번호·닉네임 — 개인정보 열람). 배송 명단은 한 번 쓰는 내려받기 주소({@code data:} — 파일을 서버에 남기지 않는다, 가정).
 */
@Service
public class GroupBuyOrderAdminFacade {

    private final GroupBuyOrderAdminQueries queries;
    private final TransactionTemplate tx;
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

    public GroupBuyOrderAdminFacade(GroupBuyOrderAdminQueries queries, PlatformTransactionManager transactions, ParticipationStore participations, GroupBuyStore groups, GroupBuyRequestStore requests, GroupBuyRefundStore refunds,
                                    FulfillmentStore fulfillmentStore, FulfillmentService fulfillment, AddressRevealService addresses,
                                    GroupBuyAdminRefundService adminRefunds, GroupBuyShipDeadlines shipDeadlines, GroupBuyAudit audit, PaymentDetailText paymentText,
                                    ObjectProvider<Clock> clocks) {
        this.queries = queries;
        this.tx = new TransactionTemplate(transactions);
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
        Long group = groupBuyId != null && !groupBuyId.isBlank() ? AdminIds.field(groupBuyId, "groupBuyId") : null;
        String v = q != null && !q.isBlank() ? q.strip() : null;
        Instant now = now();
        List<Long> onlyIds = "SHIP_OVERDUE".equals(alert) ? shipDeadlines.overdue(now, 10_000).stream().map(GroupBuyShipDeadlines.Due::participationId).toList() : null;
        Instant shippedBefore = "DELIVERY_STALE".equals(alert) ? now.minus(Duration.ofDays(CommerceGroupBuySettingsFacade.DELIVERY_STALE_DAYS)) : null;
        GroupBuyOrderAdminQueries.ListFilter filter = new GroupBuyOrderAdminQueries.ListFilter(group, v, v == null ? null : GroupBuyRowNames.like(v),
                from == null ? null : from.atStartOfDay(ServiceTime.ZONE).toInstant(), to == null ? null : to.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant(), onlyIds,
                shippedBefore, null);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ParticipationStatus st : ParticipationStatus.values()) {
            counts.put(st.name(), 0L);
        }
        queries.statusCounts(filter).forEach((st, n) -> counts.put(st.name(), n));
        if (status != null && !status.isBlank()) {
            filter = filter.withStatus(AdminIds.value(ParticipationStatus.class, status, "status"));
        }
        long total = queries.count(filter);
        List<Long> ids = queries.pageIds(filter, (long) p * s, s);
        List<GroupBuyOrderAdminView.Row> rows = new ArrayList<>();
        for (long id : ids) {
            rows.add(row(participations.find(id).orElseThrow()));
        }
        return new GroupBuyOrderAdminView.ListResponse(rows, total, counts);
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
        GroupBuyOrderAdminView.Payment payment = o.paymentOrderId() == null ? null : queries.payment(o.id(), o.paymentOrderId())
                .map(po -> new GroupBuyOrderAdminView.Payment(Long.toString(po.id()), po.paymentId(), po.status() == null ? null : po.status().name(),
                        paymentText.payMethod(po.payMethod()), po.escrow(), po.paidAt())).orElse(null);
        GroupBuyOrderAdminView.MaskedAddress address;
        if (o.addressMaskedAt() != null) {
            address = new GroupBuyOrderAdminView.MaskedAddress("", "", "", "", "", o.addressMaskedAt());
        } else {
            ShippingAddress m = addresses.masked(o.id());
            address = new GroupBuyOrderAdminView.MaskedAddress(nz(m.recipient()), nz(m.phone()), nz(m.postcode()), (nz(m.address1()) + " " + nz(m.address2())).strip(),
                    nz(m.memo()), null);
        }
        List<GroupBuyOrderAdminView.AddressView> views = queries.addressViews(o.id()).stream()
                .map(v -> new GroupBuyOrderAdminView.AddressView(nz(v.operatorName()), v.reason(), v.viewedAt())).toList();
        String memo = queries.adminMemo(o.id());
        List<GroupBuyOrderAdminView.StatusChange> history = queries.history(o.id()).stream().map(h -> new GroupBuyOrderAdminView.StatusChange(
                h.from() == null ? null : h.from().name(), h.to() == null ? null : h.to().name(), h.operatorName() != null ? h.operatorName()
                : h.actorId() == null ? "SYSTEM" : "MEMBER", h.cause(), h.createdAt())).toList();
        List<GroupBuyOrderAdminView.RefundRecord> refundRows = new ArrayList<>();
        for (GroupBuyRefundStore.RefundRow rf : refunds.ofParticipation(o.id())) {
            Optional<GroupBuyOrderAdminQueries.RefundExtra> extra = queries.refundExtra(rf.id());
            refundRows.add(new GroupBuyOrderAdminView.RefundRecord(Long.toString(rf.id()), rf.cause().name(), rf.method(), refundState(rf), rf.itemRefund(),
                    rf.shippingRefund(), rf.returnFee(), rf.reshippingFee(), rf.reshippingRefund(), rf.compensationPg() + rf.compensationBank(), rf.amount(),
                    rf.cause() == com.community.groupbuy.entity.RefundCause.SUPPLY_FAILURE || rf.cause() == com.community.groupbuy.entity.RefundCause.DELAY,
                    extra.map(GroupBuyOrderAdminQueries.RefundExtra::reason).orElse(null), extra.map(GroupBuyOrderAdminQueries.RefundExtra::operatorName).orElse(null),
                    rf.requestId() == null ? null : Long.toString(rf.requestId()), rf.createdAt(), rf.completedAt()));
        }
        for (GroupBuyOrderAdminQueries.PendingRefundRow pr : queries.pendingRefunds(o.id())) {
            refundRows.add(new GroupBuyOrderAdminView.RefundRecord("pending-" + pr.id(), "ADMIN", "PG_CANCEL", "AWAITING_APPROVAL", pr.amount(), 0, 0, 0, 0, 0,
                    pr.amount(), pr.sellerFault(), pr.reason(), pr.operatorName(), null, pr.createdAt(), null));
        }
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
        Instant now = now();
        // 예전 JDBC 자동 커밋과 같이 이 문장 하나만의 트랜잭션(감사·다시 읽기는 그 뒤 — 경계 그대로)
        if (!Boolean.TRUE.equals(tx.execute(st -> queries.saveMemo(id, memo.isEmpty() ? null : memo, now)))) {
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
        Long owner = queries.pendingRefundOwner(pendingId);
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
        GroupBuyOrderAdminQueries.MemberRow m = queries.member(o.userId());
        PaymentMemberView member = GroupBuyAdminFacade.member(o.userId(), m.nickname(), m.gone());
        List<GroupBuyOrderAdminQueries.LineRow> lines = queries.lines(o.id());
        String summary = lines.isEmpty() ? "" : lines.get(0).name() + " × " + lines.get(0).qty() + (lines.size() > 1 ? " 외 " + (lines.size() - 1) : "");
        int totalQty = lines.stream().mapToInt(GroupBuyOrderAdminQueries.LineRow::qty).sum();
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

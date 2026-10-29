package com.community.groupbuy.service;

import com.community.commerce.image.GroupBuyImageUploader;
import com.community.commerce.sales.BusinessInfo;
import com.community.commerce.sales.CommerceSalesStore;
import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.groupbuy.dto.request.GroupBuyMemberRequest;
import com.community.groupbuy.dto.response.MyGroupBuyOrderView;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.Fulfillment;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.entity.SupplyChoice;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.payment.dto.PaymentCheckout;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 공구 주문(user-web {@code /me/orders}) — 목록·상세·결제 다시 열기·배송지·본인 취소·취소/반품 요청·지연 취소·공급 불가 선택·사진. 업무는 shared 서비스(2b-2·2b-3)가 하고
 * 여기는 주문 번호 → 주문(남의 주문은 404)과 화면 모양을 맡는다. 단추 상태({@code actions})는 서비스의 규칙과 같은 판정이다(어긋나면 서비스가 409 로 막는다).
 */
@Service
public class MyGroupBuyOrderService {

    public static final int PAGE_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyRequestStore requestStore;
    private final GroupBuyRefundStore refundStore;
    private final FulfillmentStore fulfillment;
    private final CommerceSalesStore sales;
    private final GroupBuyJoinService joins;
    private final ParticipationCancelService cancels;
    private final GroupBuyRequestService requests;
    private final GroupBuySupplyService supply;
    private final AddressRevealService addresses;
    private final GroupBuyImageUploader images;
    private final GroupBuyMemberService members;
    private final ObjectProvider<Clock> clocks;

    public MyGroupBuyOrderService(DataSource dataSource, ParticipationStore participations, GroupBuyStore groups, GroupBuyRequestStore requestStore,
                                  GroupBuyRefundStore refundStore, FulfillmentStore fulfillment, CommerceSalesStore sales, GroupBuyJoinService joins,
                                  ParticipationCancelService cancels, GroupBuyRequestService requests, GroupBuySupplyService supply, AddressRevealService addresses,
                                  GroupBuyImageUploader images, GroupBuyMemberService members, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.participations = participations;
        this.groups = groups;
        this.requestStore = requestStore;
        this.refundStore = refundStore;
        this.fulfillment = fulfillment;
        this.sales = sales;
        this.joins = joins;
        this.cancels = cancels;
        this.requests = requests;
        this.supply = supply;
        this.addresses = addresses;
        this.images = images;
        this.members = members;
        this.clocks = clocks;
    }

    // ── 목록·상세 ───────────────────────────────────────────────────────────

    public MyGroupBuyOrderView.Page list(long userId, int page) {
        int p = Math.max(0, page);
        Long total = jdbc.queryForObject("SELECT count(*) FROM group_buy_participations WHERE user_id = ?", Long.class, userId);
        List<Long> ids = jdbc.queryForList("SELECT id FROM group_buy_participations WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", Long.class,
                userId, PAGE_SIZE, p * PAGE_SIZE);
        Instant now = now();
        List<MyGroupBuyOrderView.Summary> items = new ArrayList<>();
        for (long id : ids) {
            ParticipationRow o = participations.find(id).orElseThrow();
            GroupBuyRow g = groups.find(o.groupBuyId()).orElseThrow();
            List<ItemRow> lines = participations.items(id);
            Map<Long, SkuRow> skus = skus(g.id());
            ItemRow first = lines.stream().filter(ItemRow::main).findFirst().orElse(lines.isEmpty() ? null : lines.get(0));
            String firstName = first == null || (lines.size() == 1 && g.productName().equals(skuName(skus, first))) ? null : skuName(skus, first);
            items.add(new MyGroupBuyOrderView.Summary(o.orderNo(), Long.toString(g.id()), GroupBuyMemberService.title(g), members.thumbnail(g), o.status().name(), firstName,
                    lines.size(), o.amount(), o.refundedAmount(), o.createdAt(), attention(o, g, now)));
        }
        return new MyGroupBuyOrderView.Page(items, p, PAGE_SIZE, total == null ? 0 : total);
    }

    public MyGroupBuyOrderView.Detail detail(long userId, String orderNo) {
        ParticipationRow o = own(userId, orderNo);
        GroupBuyRow g = groups.find(o.groupBuyId()).orElseThrow(GroupBuyException::notFound);
        Instant now = now();
        Map<Long, SkuRow> skus = skus(g.id());
        List<ItemRow> lines = participations.items(o.id());
        List<GroupBuyRequestStore.RequestRow> reqs = requestStore.ofParticipation(o.id());
        Map<Long, Integer> inOpenRequest = new HashMap<>();
        for (GroupBuyRequestStore.RequestRow r : reqs) {
            if (RequestStatus.OPEN.contains(r.status())) {
                requestStore.items(r.id()).forEach(i -> inOpenRequest.merge(i.participationItemId(), i.qty(), Integer::sum));
            }
        }
        List<MyGroupBuyOrderView.Item> items = lines.stream().map(i -> new MyGroupBuyOrderView.Item(Long.toString(i.id()), Long.toString(i.skuId()), skuName(skus, i),
                i.main() ? "MAIN" : "ADDON", i.qty(), i.unitPrice(), i.itemAmount(), i.canceledQty(), i.returnedQty(),
                Math.max(0, i.heldQty() - inOpenRequest.getOrDefault(i.id(), 0)))).toList();
        boolean openRequest = reqs.stream().anyMatch(r -> RequestStatus.OPEN.contains(r.status()));
        Optional<Instant> delayAt = groups.firstDelayNoticeAt(g.id());
        boolean delayCancel = delayAt.isPresent() && ParticipationStatus.PAID_BEFORE_SHIPPING.contains(o.status()) && o.joinedAt() != null
                && !o.joinedAt().isAfter(delayAt.get());
        Optional<GroupBuyRequestStore.SupplyChoiceRow> choice = requestStore.choicesOf(o.id()).stream().reduce((a, b) -> b);
        boolean choicePending = choice.filter(c -> c.choice() == null && c.deadline().isAfter(now)).isPresent();
        MyGroupBuyOrderView.Address address = maskedAddress(o);
        boolean immediate = Fulfillment.IMMEDIATE.name().equals(g.fulfillment());
        boolean cancelNow = !delayCancel && (immediate ? o.status() == ParticipationStatus.CONFIRMED : o.status() == ParticipationStatus.JOINED);
        boolean requestCancel = !openRequest && !delayCancel && (immediate ? o.status() == ParticipationStatus.PREPARING
                : EnumSettings.AFTER_CLOSE_REQUEST_CANCEL.contains(o.status()));
        Instant[] windows = returnWindows(o);
        boolean requestReturn = !openRequest && o.status() == ParticipationStatus.DELIVERED && windows[1] != null && now.isBefore(windows[1])
                && lines.stream().anyMatch(i -> i.heldQty() > 0);
        MyGroupBuyOrderView.Actions actions = new MyGroupBuyOrderView.Actions(
                o.status() == ParticipationStatus.PENDING_PAYMENT && o.reservedUntil() != null && Duration.between(now, o.reservedUntil()).compareTo(Duration.ofMinutes(1)) >= 0,
                !address.purged(), !address.purged() && AddressRevealService.editable(o, g), cancelNow, requestCancel, requestReturn, delayCancel, choicePending);
        MyGroupBuyOrderView.DelayNotice delay = delayAt.isEmpty() ? null : jdbc.query("SELECT previous_planned_at, new_planned_at, reason, created_at "
                        + "FROM group_buy_delay_notices WHERE group_buy_id = ? ORDER BY created_at DESC LIMIT 1", (rs, i) -> new MyGroupBuyOrderView.DelayNotice(
                        rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate(), rs.getString(3), rs.getTimestamp(4).toInstant(),
                        o.remainingCash()), g.id()).stream().findFirst().orElse(null);
        MyGroupBuyOrderView.SupplyChoice supplyChoice = choice.map(c -> {
            Map<String, Object> f = jdbc.queryForMap("SELECT sku_id, reason FROM group_buy_supply_failures WHERE id = ?", c.supplyFailureId());
            SkuRow sku = skus.get(((Number) f.get("sku_id")).longValue());
            return new MyGroupBuyOrderView.SupplyChoice(sku == null ? "" : sku.name(), (String) f.get("reason"), c.deadline(), c.choice() == null ? null : c.choice().name(),
                    c.defaulted(), o.remainingCash());
        }).orElse(null);
        MyGroupBuyOrderView.Shipment shipment = fulfillment.shipmentsOf(o.id()).stream().reduce((a, b) -> b).map(this::shipment).orElse(null);
        return new MyGroupBuyOrderView.Detail(o.orderNo(), o.status().name(), new MyGroupBuyOrderView.GroupBuyRef(Long.toString(g.id()), GroupBuyMemberService.title(g),
                members.thumbnail(g), g.status().name(), g.fulfillment(), g.closeReason(), g.roundNo()), items, o.itemsAmount(), o.shippingFee(), o.amount(),
                o.refundedAmount(), o.compensationAmount(), o.createdAt(), o.joinedAt(), o.status() == ParticipationStatus.PENDING_PAYMENT ? o.reservedUntil() : null,
                g.shipPlannedAt(), immediate ? g.shipWithinBusinessDays() : null, address, null, shipment, delayCancel ? delay : null, supplyChoice,
                reqs.stream().map(r -> request(r, skus, lines)).toList(), refunds(o.id()), windows[0], windows[1], actions);
    }

    // ── 결제·배송지 ─────────────────────────────────────────────────────────

    /** 결제 다시 열기(결정 41·63 — 선점을 늘리지 않는다) */
    public com.community.groupbuy.dto.response.GroupBuyMemberView.PaymentStart reopen(long userId, String orderNo, boolean afterFailure) {
        ParticipationRow o = own(userId, orderNo);
        PaymentCheckout checkout = joins.reopen(userId, o.id(), afterFailure);
        ShippingAddress a = safeAddress(userId, o.id());
        return members.paymentStart("GROUP_BUY", o.id(), o.orderNo(), checkout, userId, a == null ? null : a.recipient(), a == null ? null : a.phone());
    }

    public MyGroupBuyOrderView.Address fullAddress(long userId, String orderNo) {
        ParticipationRow o = own(userId, orderNo);
        ShippingAddress a = addresses.memberView(userId, o.id());
        return new MyGroupBuyOrderView.Address(a.recipient(), a.phone(), a.postcode(), a.address1(), nz(a.address2()), nz(a.memo()), false, false);
    }

    public MyGroupBuyOrderView.Detail updateAddress(long userId, String orderNo, GroupBuyMemberRequest.Address body) {
        ParticipationRow o = own(userId, orderNo);
        GroupBuyMemberRequest.Address a = body == null ? new GroupBuyMemberRequest.Address(null, null, null, null, null, null) : body;
        addresses.memberUpdate(userId, o.id(), new ShippingAddress(a.recipient(), a.phone(), a.postcode(), a.address1(), a.address2(), a.memo()));
        return detail(userId, orderNo);
    }

    // ── 취소·요청 ───────────────────────────────────────────────────────────

    public MyGroupBuyOrderView.CancelPreview cancelPreview(long userId, String orderNo, GroupBuyMemberRequest.Cancel body) {
        ParticipationRow o = own(userId, orderNo);
        GroupBuyRequestService.Preview p = requests.preview(userId, o.id(), RequestType.CANCEL, lineInputs(body == null ? null : body.lines(), o.id()));
        GroupBuyRefundCalculator.Result r = p.cancel();
        return new MyGroupBuyOrderView.CancelPreview(r.total(), r.itemRefund(), r.shippingRefund() + r.reshippingRefund(), r.reshippingFee());
    }

    public MyGroupBuyOrderView.Detail cancel(long userId, String orderNo, GroupBuyMemberRequest.Cancel body) {
        ParticipationRow o = own(userId, orderNo);
        cancels.cancelByMember(userId, o.id(), GroupBuyMemberService.lines(body == null ? null : body.lines(), "lines"));
        return detail(userId, orderNo);
    }

    /** 발송 지연 공지 뒤 즉시 전액 취소(결정 52 — 줄을 고르지 않는다) */
    public MyGroupBuyOrderView.Detail delayCancel(long userId, String orderNo) {
        ParticipationRow o = own(userId, orderNo);
        cancels.cancelByMember(userId, o.id(), List.of());
        return detail(userId, orderNo);
    }

    public MyGroupBuyOrderView.ReturnPreview returnPreview(long userId, String orderNo, GroupBuyMemberRequest.ReturnPreview body) {
        ParticipationRow o = own(userId, orderNo);
        GroupBuyRequestService.Preview p = requests.preview(userId, o.id(), RequestType.RETURN, requestLines(body == null ? null : body.items(), true));
        Map<String, MyGroupBuyOrderView.ReturnOutcome> byMethod = new LinkedHashMap<>();
        p.byMethod().forEach((m, r) -> byMethod.put(m.name(), new MyGroupBuyOrderView.ReturnOutcome(r.total(), r.itemRefund(), r.shippingRefund() + r.reshippingRefund(),
                r.returnFee(), r.reshippingFee(), r.sellerFault() && m == CollectionMethod.CUSTOMER_SEND)));
        BusinessInfo b = sales.businessInfo();
        return new MyGroupBuyOrderView.ReturnPreview(p.sellerFault() ? "COMPANY" : "CUSTOMER", byMethod, String.join(" ", nz(b.companyName()), nz(b.address())).strip());
    }

    public MyGroupBuyOrderView.RequestCreated createRequest(long userId, String orderNo, GroupBuyMemberRequest.CreateRequest body) {
        ParticipationRow o = own(userId, orderNo);
        GroupBuyMemberRequest.CreateRequest b = body == null ? new GroupBuyMemberRequest.CreateRequest(null, null, null, null, null, null, null) : body;
        RequestType type = parse(RequestType.class, b.type(), "type");
        CollectionMethod method = b.collectionMethod() == null ? null : parse(CollectionMethod.class, b.collectionMethod(), "collectionMethod");
        Resolution resolution = b.resolution() == null ? null : parse(Resolution.class, b.resolution(), "resolution");
        List<Long> photos = new ArrayList<>();
        if (b.photoIds() != null) {
            for (String id : b.photoIds()) {
                photos.add(GroupBuyMemberService.id(id, "photoIds"));
            }
        }
        GroupBuyRequestService.Created created = requests.create(userId, o.id(), new GroupBuyRequestService.RequestInput(type, method, resolution,
                requestLines(b.items(), type == RequestType.RETURN), b.reasonText(), photos, b.expectedAmount()));
        GroupBuyRow g = groups.find(o.groupBuyId()).orElseThrow();
        GroupBuyRequestStore.RequestRow row = requestStore.find(created.requestId()).orElseThrow();
        com.community.groupbuy.dto.response.GroupBuyMemberView.PaymentStart fee = null;
        if (created.feePayment() != null) {
            ShippingAddress a = safeAddress(userId, o.id());
            fee = members.paymentStart("GROUP_BUY_RETURN_FEE", created.requestId(), o.orderNo(), created.feePayment(), userId, a == null ? null : a.recipient(),
                    a == null ? null : a.phone());
        }
        return new MyGroupBuyOrderView.RequestCreated(request(row, skus(g.id()), participations.items(o.id())), fee);
    }

    public com.community.groupbuy.dto.response.GroupBuyMemberView.PaymentStart reopenFee(long userId, String orderNo, String requestId) {
        ParticipationRow o = own(userId, orderNo);
        long id = ownRequest(o, requestId);
        PaymentCheckout checkout = requests.reopenFeePayment(userId, id);
        ShippingAddress a = safeAddress(userId, o.id());
        return members.paymentStart("GROUP_BUY_RETURN_FEE", id, o.orderNo(), checkout, userId, a == null ? null : a.recipient(), a == null ? null : a.phone());
    }

    public MyGroupBuyOrderView.Detail withdraw(long userId, String orderNo, String requestId) {
        ParticipationRow o = own(userId, orderNo);
        requests.withdraw(userId, ownRequest(o, requestId));
        return detail(userId, orderNo);
    }

    public MyGroupBuyOrderView.Detail supplyChoice(long userId, String orderNo, GroupBuyMemberRequest.SupplyChoice body) {
        ParticipationRow o = own(userId, orderNo);
        supply.choose(userId, o.id(), parse(SupplyChoice.class, body == null ? null : body.choice(), "choice"));
        return detail(userId, orderNo);
    }

    /** 하자·오배송 사진(요청 전에 먼저 올린다 — 붙지 않으면 24시간 뒤 정리) */
    public MyGroupBuyOrderView.Photo photo(long userId, String orderNo, MultipartFile file) {
        own(userId, orderNo);
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        GroupBuyImageUploader.Uploaded up = images.upload("request", data);
        long id = requests.registerMemberPhoto(userId, up.key());
        return new MyGroupBuyOrderView.Photo(Long.toString(id), up.url());
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 화면 단추 판정용 상태 묶음 */
    private static final class EnumSettings {
        static final java.util.Set<ParticipationStatus> AFTER_CLOSE_REQUEST_CANCEL = java.util.EnumSet.of(ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING);
    }

    private ParticipationRow own(long userId, String orderNo) {
        Long id = jdbc.query("SELECT id FROM group_buy_participations WHERE order_no = ? AND user_id = ?", (rs, i) -> rs.getLong(1), orderNo, userId).stream().findFirst()
                .orElseThrow(GroupBuyException::orderNotFound);
        return participations.find(id).orElseThrow(GroupBuyException::orderNotFound);
    }

    private long ownRequest(ParticipationRow o, String requestId) {
        long id;
        try {
            id = Long.parseLong(requestId);
        } catch (NumberFormatException e) {
            throw GroupBuyException.requestNotFound();
        }
        return requestStore.find(id).filter(r -> r.participationId() == o.id()).map(GroupBuyRequestStore.RequestRow::id).orElseThrow(GroupBuyException::requestNotFound);
    }

    private ShippingAddress safeAddress(long userId, long participationId) {
        try {
            return addresses.memberView(userId, participationId);
        } catch (RuntimeException e) {
            return null; // 지웠거나 키가 없다 — 구매자 칸은 비운다
        }
    }

    private Map<Long, SkuRow> skus(long groupBuyId) {
        Map<Long, SkuRow> out = new HashMap<>();
        groups.skus(groupBuyId).forEach(s -> out.put(s.id(), s));
        return out;
    }

    private static String skuName(Map<Long, SkuRow> skus, ItemRow i) {
        SkuRow s = skus.get(i.skuId());
        return s == null ? "" : s.name();
    }

    private String attention(ParticipationRow o, GroupBuyRow g, Instant now) {
        if (o.status() == ParticipationStatus.PENDING_PAYMENT && o.reservedUntil() != null && o.reservedUntil().isAfter(now)) {
            return "PAY";
        }
        if (requestStore.choicePending(o.id())) {
            return "SUPPLY_CHOICE";
        }
        if (requestStore.openOf(o.id()).stream().anyMatch(r -> r.status() == RequestStatus.AWAITING_FEE)) {
            return "RETURN_FEE";
        }
        Optional<Instant> delay = groups.firstDelayNoticeAt(g.id());
        if (delay.isPresent() && ParticipationStatus.PAID_BEFORE_SHIPPING.contains(o.status()) && o.joinedAt() != null && !o.joinedAt().isAfter(delay.get())) {
            return "DELAY";
        }
        return null;
    }

    private MyGroupBuyOrderView.Address maskedAddress(ParticipationRow o) {
        if (o.addressMaskedAt() != null) {
            return new MyGroupBuyOrderView.Address("", "", "", "", "", "", true, true);
        }
        ShippingAddress m = addresses.masked(o.id());
        return new MyGroupBuyOrderView.Address(nz(m.recipient()), nz(m.phone()), nz(m.postcode()), nz(m.address1()), nz(m.address2()), nz(m.memo()), true, false);
    }

    private MyGroupBuyOrderView.Shipment shipment(FulfillmentStore.ShipmentRow s) {
        FulfillmentStore.CarrierRow c = fulfillment.carriers().stream().filter(x -> x.code().equals(s.carrierCode())).findFirst().orElse(null);
        return new MyGroupBuyOrderView.Shipment(c == null ? s.carrierCode() : c.name(), s.trackingNo(), c == null ? null : c.trackingUrl().replace("{no}", s.trackingNo()),
                s.kind(), s.shippedAt(), s.deliveredAt());
    }

    /** [단순 변심 반품 기한, 하자·오배송 반품 기한] — 배송완료(한국 날짜) + 7일 / + 3개월 그날 끝(계약 2b-3 「정한 세부」 3) */
    private static Instant[] returnWindows(ParticipationRow o) {
        if (o.deliveredAt() == null) {
            return new Instant[] {null, null};
        }
        LocalDate day = ServiceTime.dateOf(o.deliveredAt());
        return new Instant[] {day.plusDays(8).atStartOfDay(ServiceTime.ZONE).toInstant(), day.plusMonths(3).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant()};
    }

    private MyGroupBuyOrderView.Request request(GroupBuyRequestStore.RequestRow r, Map<Long, SkuRow> skus, List<ItemRow> lines) {
        List<MyGroupBuyOrderView.RequestItem> items = requestStore.items(r.id()).stream().map(i -> {
            SkuRow s = skus.get(i.skuId());
            return new MyGroupBuyOrderView.RequestItem(Long.toString(i.skuId()), s == null ? "" : s.name(), s == null || s.main() ? "MAIN" : "ADDON", i.qty(),
                    i.reason() == null ? null : i.reason().name());
        }).toList();
        Long refund = r.status() == RequestStatus.COMPLETED ? refundStore.ofRequest(r.id()).stream().mapToLong(GroupBuyRefundStore.RefundRow::amount).sum() : null;
        Long feeDue = r.status() == RequestStatus.AWAITING_FEE ? r.feeAmount() - r.feePaidAmount() : null;
        Instant handled = jdbc.query("SELECT COALESCE(completed_at, decided_at) FROM group_buy_requests WHERE id = ?", (rs, i) -> GroupBuyMemberService.ts(rs.getTimestamp(1)),
                r.id()).stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        String tracking = jdbc.query("SELECT pickup_tracking_no FROM group_buy_requests WHERE id = ?", (rs, i) -> rs.getString(1), r.id()).stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        boolean withdraw = EnumSetHolder.WITHDRAWABLE.contains(r.status());
        return new MyGroupBuyOrderView.Request(Long.toString(r.id()), r.type().name(), status(r.status()), items, r.method() == null ? null : r.method().name(),
                r.resolution() == null ? null : r.resolution().name(), r.previewAmount(), refund, feeDue, r.rejectReasonCode(), tracking, r.createdAt(), handled,
                new MyGroupBuyOrderView.RequestActions(withdraw, r.status() == RequestStatus.AWAITING_FEE));
    }

    private static final class EnumSetHolder {
        static final java.util.Set<RequestStatus> WITHDRAWABLE = java.util.EnumSet.of(RequestStatus.AWAITING_FEE, RequestStatus.REQUESTED, RequestStatus.APPROVED);
    }

    /** 서버 요청 상태 → 화면 이름(부족분 결제 전 = AWAITING_PAYMENT, 결정 68) */
    static String status(RequestStatus s) {
        return s == RequestStatus.AWAITING_FEE ? "AWAITING_PAYMENT" : s.name();
    }

    private List<MyGroupBuyOrderView.Refund> refunds(long participationId) {
        return refundStore.ofParticipation(participationId).stream().map(r -> new MyGroupBuyOrderView.Refund(Long.toString(r.id()), r.cause().name(), r.amount(),
                r.status().name(), r.method(), r.createdAt(), r.completedAt())).toList();
    }

    private static List<GroupBuyRequestService.LineInput> lineInputs(List<GroupBuyMemberRequest.Line> lines, long participationId) {
        List<GroupBuyRequestService.LineInput> out = new ArrayList<>();
        for (OrderLine l : GroupBuyMemberService.lines(lines, "lines")) {
            out.add(new GroupBuyRequestService.LineInput(l.skuId(), l.qty(), null));
        }
        return out;
    }

    private static List<GroupBuyRequestService.LineInput> requestLines(List<GroupBuyMemberRequest.RequestLine> lines, boolean withReason) {
        List<GroupBuyRequestService.LineInput> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (GroupBuyMemberRequest.RequestLine l : lines) {
            if (l == null || l.qty() == null) {
                throw ValidationException.of("items", new MessageRef("groupbuy.order.items.invalid"));
            }
            RequestReason reason = withReason ? parse(RequestReason.class, l.reason(), "items") : null;
            out.add(new GroupBuyRequestService.LineInput(GroupBuyMemberService.id(l.skuId(), "items"), l.qty(), reason));
        }
        return out;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value == null ? "" : value);
        } catch (IllegalArgumentException e) {
            throw ValidationException.of(field, new MessageRef("groupbuy.value.invalid"));
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }
}

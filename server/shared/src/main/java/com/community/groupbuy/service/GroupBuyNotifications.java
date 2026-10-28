package com.community.groupbuy.service;

import com.community.commerce.sales.BusinessInfo;
import com.community.commerce.sales.CommerceSalesStore;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.mail.outbox.dto.OutboxMail;
import com.community.mail.outbox.service.MailOutbox;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 공구 업무 이벤트({@link GroupBuyEvents}) → 회원 알림·메일·운영자 알림(커머스 2b-4, 계약 결정 12·16·31·40·52·53·57·81).
 *
 * <ul>
 *   <li><b>트랜잭션</b>: 이벤트를 낸 <b>업무 트랜잭션 안에서</b> 동기로 받는다({@code @EventListener}) — 업무가 롤백되면 알림·메일도 없다. 회원 알림·운영자 알림은 발행기의 세이브포인트 안이라
 *       실패해도 업무를 깨지 않는다. 메일은 커머스 메일 대기열({@link MailOutbox} — 같은 트랜잭션, 메일 서버가 죽어 있어도 업무는 성공하고 나중에 나간다, P27).</li>
 *   <li><b>잠금</b>: 결제 확정·취소 완료(결제 행을 쥔 채 불린다 — 결정 37) 안에서도 불리므로 공구·옵션 행을 잠그지 않는다. 읽기와 관심 신청 행의 조건부 갱신만 한다.</li>
 *   <li><b>꼭 가는 메일</b>(회원이 끌 수 없다): 주문 확인 = 계약 내용 통지 {@code GROUP_BUY_ORDER_CONFIRMED}(결정 57) · 환불 완료 {@code GROUP_BUY_REFUND_COMPLETED} ·
 *       주문 안내 {@code GROUP_BUY_ORDER_NOTICE}(발송 지연 — 제15조 ② · 공급 불가 선택 · 결제를 받지 못한 자동 취소). 같은 일은 한 통(대기열 중복 열쇠).</li>
 *   <li><b>수량 풀림</b>(결정 81): 공구와 그 단품의 남은 수량이 <b>둘 다 0보다 클 때만</b>, 같은 신청은 <b>10분에 한 번</b>({@code group_buy_watches.last_notified_at} 조건부 갱신 —
 *       동시에 두 번 풀려도 한 번). 공구 단위 신청(단품 없음)은 공구 남은 수량만 본다.</li>
 *   <li>메일 변수의 글(판매 조건·발송 문구)은 사이트 기본 언어(한국어)로 만든다 — 대기열은 변수 값을 그대로 쓴다(가정, 계약 「2b-4 결과」).</li>
 * </ul>
 */
@Component
public class GroupBuyNotifications {

    /** 같은 수량 풀림 신청에 다시 알리기까지(결정 81) */
    public static final Duration RESTOCK_INTERVAL = Duration.ofMinutes(10);

    /** 회원 알림 종류(번들 {@code member.notification.GROUP_BUY.kind.<이름>}) */
    public enum Kind {
        PAID, REJECTED, AUTO_CANCELED, HOLD_EXPIRED, RESTOCK, CLOSING_SOON, EXTENDED, ROUND_STARTED, REFUNDED, REFUND_MANUAL, DELAY, SUPPLY_CHOICE,
        JUDGED_SUCCESS, JUDGED_FAILED, GROUP_CANCELED, REQUEST_REQUESTED, REQUEST_APPROVED, REQUEST_RECEIVED, REQUEST_COMPLETED, REQUEST_REJECTED, REQUEST_EXPIRED,
        REQUEST_SUPERSEDED, REQUEST_EXCHANGE_SHIPPED, REQUEST_EXCHANGE_TO_REFUND
    }

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Locale MAIL_LOCALE = Locale.KOREAN;

    /** 알림에 쓰는 주문 한 줄 */
    record Order(long id, String orderNo, long groupBuyId, long userId, long amount, long shippingFee, String title, String status) {
    }

    private final JdbcTemplate jdbc;
    private final MemberNotificationPublisher members;
    private final NotificationPublisher operators;
    private final MailOutbox outbox;
    private final CommerceSalesStore sales;
    private final Messages messages;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyNotifications(DataSource dataSource, MemberNotificationPublisher members, NotificationPublisher operators, MailOutbox outbox, CommerceSalesStore sales,
                                 Messages messages, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.members = members;
        this.operators = operators;
        this.outbox = outbox;
        this.sales = sales;
        this.messages = messages;
        this.clocks = clocks;
    }

    // ── 주문·결제 ───────────────────────────────────────────────────────────

    /** 결제 확정 — 주문 확인 알림 + 계약 내용 통지 메일(결정 57) */
    @EventListener
    public void on(GroupBuyEvents.OrderPaid e) {
        order(e.participationId()).ifPresent(o -> {
            members.groupBuy(o.userId(), Kind.PAID.name(), o.groupBuyId(), o.title(), o.amount(), o.orderNo(), orderPath(o), false);
            outbox.enqueue(new OutboxMail("GB_ORDER_CONFIRMED:" + o.id(), o.userId(), RequiredMail.GROUP_BUY_ORDER_CONFIRMED, contractVariables(o)));
        });
    }

    /** 결제를 받지 않았다(도메인 거절 — 자동 취소 안내). 불변식이 깨진 거절이면 운영자 주문 이상(CRITICAL) */
    @EventListener
    public void on(GroupBuyEvents.OrderRejected e) {
        order(e.participationId()).ifPresent(o -> {
            members.groupBuy(o.userId(), Kind.REJECTED.name(), o.groupBuyId(), o.title(), o.amount(), o.orderNo(), orderPath(o), false);
            notice(o, "GB_REJECTED:" + o.id(), Kind.REJECTED);
            if (e.anomaly()) {
                anomaly(o);
            }
        });
    }

    /** 선점 만료(본인 취소가 아니면 — 10-6 4) */
    @EventListener
    public void on(GroupBuyEvents.HoldExpired e) {
        order(e.participationId()).ifPresent(o ->
                members.groupBuy(o.userId(), Kind.HOLD_EXPIRED.name(), o.groupBuyId(), o.title(), o.amount(), o.orderNo(), groupBuyPath(o.groupBuyId()), false));
    }

    /** 자동 취소(늦은 결제·도메인 거절)의 환불 완료 — 금액은 결제 행(결정 40) */
    @EventListener
    public void on(GroupBuyEvents.AutoCancelCompleted e) {
        order(e.participationId()).ifPresent(o -> {
            Long amount = jdbc.query("SELECT amount FROM payment_orders WHERE id = ?", (rs, i) -> rs.getLong(1), e.paymentOrderId()).stream().findFirst().orElse(0L);
            members.groupBuy(o.userId(), Kind.AUTO_CANCELED.name(), o.groupBuyId(), o.title(), amount, o.orderNo(), orderPath(o), false);
            refundMail(o, "GB_AUTO_CANCEL:" + e.paymentOrderId(), amount, "PG_CANCEL");
        });
    }

    /** 환불 완료(PG 취소 성공·계좌 송금 완료) — 알림 + 메일 */
    @EventListener
    public void on(GroupBuyEvents.RefundCompleted e) {
        refund(e.refundId()).ifPresent(r -> order(r.participationId).ifPresent(o -> {
            members.groupBuy(o.userId(), Kind.REFUNDED.name(), o.groupBuyId(), o.title(), r.amount, o.orderNo(), orderPath(o), false);
            refundMail(o, "GB_REFUND_DONE:" + e.refundId(), r.amount, r.method);
        }));
    }

    /** 운영자가 콘솔·계좌로 직접 처리한 환불(결정 16 — 「환불 처리됨(운영자 확인)」) */
    @EventListener
    public void on(GroupBuyEvents.RefundResolvedManually e) {
        refund(e.refundId()).ifPresent(r -> order(r.participationId).ifPresent(o -> {
            members.groupBuy(o.userId(), Kind.REFUND_MANUAL.name(), o.groupBuyId(), o.title(), r.amount, o.orderNo(), orderPath(o), false);
            refundMail(o, "GB_REFUND_MANUAL:" + e.refundId(), r.amount, "BANK_TRANSFER".equals(e.method()) ? "BANK_TRANSFER" : "MANUAL");
        }));
    }

    // ── 관심·수량 풀림·연장·차수 ─────────────────────────────────────────────

    /** 수량 풀림(결정 81) — 공구·단품 둘 다 남을 때만, 같은 신청은 10분에 한 번 */
    @EventListener
    public void on(GroupBuyEvents.QtyReleased e) {
        Map<String, Object> g = jdbc.queryForList("SELECT g.status, g.max_qty - g.current_qty - g.reserved_qty AS remaining, COALESCE(NULLIF(g.title, ''), p.name) AS title FROM group_buys g "
                + "JOIN group_buy_products p ON p.id = g.product_id WHERE g.id = ?", e.groupBuyId()).stream().findFirst().orElse(null);
        if (g == null || !"RECRUITING".equals(g.get("status"))) {
            return;
        }
        long groupRemaining = ((Number) g.get("remaining")).longValue();
        Long skuRemaining = null;
        boolean skuMain = true;
        if (e.skuId() != null) {
            Map<String, Object> s = jdbc.queryForList("SELECT kind, max_qty, current_qty, reserved_qty, on_sale FROM group_buy_skus WHERE id = ? AND group_buy_id = ?",
                    e.skuId(), e.groupBuyId()).stream().findFirst().orElse(null);
            if (s == null || !Boolean.TRUE.equals(s.get("on_sale"))) {
                return;
            }
            skuMain = "MAIN".equals(s.get("kind"));
            skuRemaining = s.get("max_qty") == null ? Long.MAX_VALUE
                    : ((Number) s.get("max_qty")).longValue() - ((Number) s.get("current_qty")).longValue() - ((Number) s.get("reserved_qty")).longValue();
        }
        Timestamp now = Timestamp.from(now());
        Timestamp before = Timestamp.from(now().minus(RESTOCK_INTERVAL));
        Set<Long> users = new LinkedHashSet<>();
        if (e.skuId() != null && skuRemaining > 0 && (!skuMain || groupRemaining > 0)) {
            users.addAll(jdbc.queryForList("UPDATE group_buy_watches SET last_notified_at = ? WHERE group_buy_id = ? AND kind = 'RESTOCK' AND sku_id = ? "
                    + "AND (last_notified_at IS NULL OR last_notified_at <= ?) RETURNING user_id", Long.class, now, e.groupBuyId(), e.skuId(), before));
        }
        if (groupRemaining > 0 && (skuRemaining == null || skuRemaining > 0 || !skuMain)) {
            users.addAll(jdbc.queryForList("UPDATE group_buy_watches SET last_notified_at = ? WHERE group_buy_id = ? AND kind = 'RESTOCK' AND sku_id IS NULL "
                    + "AND (last_notified_at IS NULL OR last_notified_at <= ?) RETURNING user_id", Long.class, now, e.groupBuyId(), before));
        }
        String title = (String) g.get("title");
        for (long user : users) {
            members.groupBuy(user, Kind.RESTOCK.name(), e.groupBuyId(), title, 0, null, groupBuyPath(e.groupBuyId()), true);
        }
    }

    /** 모집 연장 — 마감 임박·수량 풀림 신청자에게 한 번씩 */
    @EventListener
    public void on(GroupBuyEvents.Extended e) {
        String title = title(e.groupBuyId());
        String until = MINUTE.format(e.until().atZone(ServiceTime.ZONE));
        for (long user : jdbc.queryForList("SELECT DISTINCT user_id FROM group_buy_watches WHERE group_buy_id = ? AND kind IN ('CLOSING_SOON', 'RESTOCK')", Long.class,
                e.groupBuyId())) {
            members.groupBuy(user, Kind.EXTENDED.name(), e.groupBuyId(), title, 0, until, groupBuyPath(e.groupBuyId()), true);
        }
    }

    /** 추가 차수 모집 시작 — 루트의 모든 차수 신청자(같은 회원 한 번 — 받는 사람은 서비스가 이미 모았다, 결정 31) */
    @EventListener
    public void on(GroupBuyEvents.RoundStarted e) {
        String title = title(e.groupBuyId());
        for (long user : new LinkedHashSet<>(e.recipients())) {
            members.groupBuy(user, Kind.ROUND_STARTED.name(), e.groupBuyId(), title, 0, Integer.toString(e.roundNo()), groupBuyPath(e.groupBuyId()), true);
        }
    }

    /**
     * 마감 임박 알림(1시간 점검 워커가 부른다) — 모집 마감이 {@code within} 안인 모집 중 공구의 CLOSING_SOON 신청을 한 번씩(알린 신청은 {@code last_notified_at}).
     *
     * @return 알린 회원 수
     */
    public int closingSoon(Duration within) {
        Instant now = now();
        int sent = 0;
        List<Map<String, Object>> rows = jdbc.queryForList("UPDATE group_buy_watches w SET last_notified_at = ? FROM group_buys g WHERE g.id = w.group_buy_id "
                        + "AND w.kind = 'CLOSING_SOON' AND w.last_notified_at IS NULL AND g.status = 'RECRUITING' AND COALESCE(g.extended_until, g.end_at) <= ? "
                        + "AND COALESCE(g.extended_until, g.end_at) > ? RETURNING w.user_id, g.id AS group_buy_id, COALESCE(g.extended_until, g.end_at) AS deadline",
                Timestamp.from(now), Timestamp.from(now.plus(within)), Timestamp.from(now));
        for (Map<String, Object> row : rows) {
            long groupBuyId = ((Number) row.get("group_buy_id")).longValue();
            Instant deadline = ((Timestamp) row.get("deadline")).toInstant();
            members.groupBuy(((Number) row.get("user_id")).longValue(), Kind.CLOSING_SOON.name(), groupBuyId, title(groupBuyId), 0,
                    MINUTE.format(deadline.atZone(ServiceTime.ZONE)), groupBuyPath(groupBuyId), true);
            sent++;
        }
        return sent;
    }

    // ── 공구 상태·발송 지연·공급 불가·요청 ─────────────────────────────────────

    /** 판정(성사·불발)·중단 — 그때 진행 중인 주문의 회원에게 한 번, 중단은 운영자 알림도 */
    @EventListener
    public void on(GroupBuyEvents.StatusChanged e) {
        Kind kind = switch (e.to()) {
            case "CLOSED_SUCCESS" -> Kind.JUDGED_SUCCESS;
            case "CLOSED_FAILED" -> Kind.JUDGED_FAILED;
            case "CANCELED" -> Kind.GROUP_CANCELED;
            default -> null;
        };
        if (kind == null) {
            return;
        }
        String title = title(e.groupBuyId());
        for (Map<String, Object> o : jdbc.queryForList("SELECT DISTINCT ON (user_id) user_id, order_no FROM group_buy_participations WHERE group_buy_id = ? "
                + "AND status IN ('PENDING_PAYMENT', 'JOINED', 'CONFIRMED', 'PREPARING', 'SHIPPED') ORDER BY user_id, created_at DESC", e.groupBuyId())) {
            String orderNo = (String) o.get("order_no");
            members.groupBuy(((Number) o.get("user_id")).longValue(), kind.name(), e.groupBuyId(), title, 0, orderNo, "/me/orders/" + orderNo, false);
        }
        if (kind == Kind.GROUP_CANCELED) {
            operators.publish(AdminNotificationEvent.COMMERCE_GROUPBUY_CANCELED, NotificationContext.empty().dedupe(Long.toString(e.groupBuyId()))
                    .link("groupBuyId", Long.toString(e.groupBuyId())));
        }
    }

    /** 발송 지연 공지(결정 52 — 제15조 ② 지체 없는 통지) — 공지 전에 결제한 발송 전 주문 전원에게 앱 + 메일 */
    @EventListener
    public void on(GroupBuyEvents.DelayAnnounced e) {
        String date = e.newPlannedAt().toString();
        String title = title(e.groupBuyId());
        for (Order o : orders("SELECT " + ORDER_COLUMNS + ORDER_FROM + " WHERE pa.group_buy_id = ? AND pa.status IN ('JOINED', 'CONFIRMED', 'PREPARING')", e.groupBuyId())) {
            members.groupBuy(o.userId(), Kind.DELAY.name(), o.groupBuyId(), title, o.amount(), date, orderPath(o), false);
            outbox.enqueue(new OutboxMail("GB_DELAY:" + o.id() + ":" + date, o.userId(), RequiredMail.GROUP_BUY_ORDER_NOTICE,
                    noticeVariables(o, text(new MessageRef("member.notification.GROUP_BUY.kind.DELAY", amount(o.amount()), date)))));
        }
    }

    /** 단품 공급 불가로 「나머지만 받기 / 전체 취소」를 묻는다(결정 53 — 앱 + 메일) */
    @EventListener
    public void on(GroupBuyEvents.SupplyChoiceAsked e) {
        order(e.participationId()).ifPresent(o -> {
            String deadline = MINUTE.format(e.deadline().atZone(ServiceTime.ZONE));
            members.groupBuy(o.userId(), Kind.SUPPLY_CHOICE.name(), o.groupBuyId(), o.title(), o.amount(), deadline, orderPath(o), false);
            outbox.enqueue(new OutboxMail("GB_SUPPLY:" + o.id() + ":" + e.skuId(), o.userId(), RequiredMail.GROUP_BUY_ORDER_NOTICE,
                    noticeVariables(o, text(new MessageRef("member.notification.GROUP_BUY.kind.SUPPLY_CHOICE", amount(o.amount()), deadline)))));
        });
    }

    /** 요청 상태가 바뀌었다 — 회원 알림(본인이 한 철회·부족분 결제 전은 빼고), 접수는 운영자 요청 알림 */
    @EventListener
    public void on(GroupBuyEvents.RequestChanged e) {
        Kind kind = requestKind(e.status(), e.reason());
        order(e.participationId()).ifPresent(o -> {
            if (kind != null) {
                members.groupBuy(o.userId(), kind.name(), o.groupBuyId(), o.title(), 0, o.orderNo(), orderPath(o), false);
            }
            if ("REQUESTED".equals(e.status())) {
                operators.publish(AdminNotificationEvent.COMMERCE_REFUND_REQUESTED, NotificationContext.empty().dedupe("intake:" + e.requestId()));
            }
        });
    }

    static Kind requestKind(String status, String reason) {
        if ("EXCHANGE_SHIPPED".equals(reason)) {
            return Kind.REQUEST_EXCHANGE_SHIPPED;
        }
        if ("EXCHANGE_TO_REFUND".equals(reason)) {
            return Kind.REQUEST_EXCHANGE_TO_REFUND;
        }
        return switch (status) {
            case "REQUESTED" -> Kind.REQUEST_REQUESTED;
            case "APPROVED" -> Kind.REQUEST_APPROVED;
            case "RECEIVED" -> Kind.REQUEST_RECEIVED;
            case "COMPLETED" -> Kind.REQUEST_COMPLETED;
            case "REJECTED" -> Kind.REQUEST_REJECTED;
            case "EXPIRED" -> Kind.REQUEST_EXPIRED;
            case "SUPERSEDED" -> Kind.REQUEST_SUPERSEDED;
            default -> null; // WITHDRAWN(본인이 했다)·AWAITING_FEE(결제 전)
        };
    }

    /** 에스크로 호출을 끝내 그만뒀다(결정 64) — 운영자 주문 이상 */
    @EventListener
    public void on(GroupBuyEvents.EscrowGaveUp e) {
        order(e.participationId()).ifPresent(this::anomaly);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private static final String ORDER_COLUMNS = "pa.id, pa.order_no, pa.group_buy_id, pa.user_id, pa.amount, pa.shipping_fee, COALESCE(NULLIF(g.title, ''), p.name), pa.status";
    private static final String ORDER_FROM = " FROM group_buy_participations pa JOIN group_buys g ON g.id = pa.group_buy_id JOIN group_buy_products p ON p.id = g.product_id";

    private Optional<Order> order(long participationId) {
        return orders("SELECT " + ORDER_COLUMNS + ORDER_FROM + " WHERE pa.id = ?", participationId).stream().findFirst();
    }

    private List<Order> orders(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6),
                titleOf(rs.getString(7)), rs.getString(8)), args);
    }

    /** 공구 제목 = 공구 이름(없으면 상품 이름) */
    private String title(long groupBuyId) {
        return jdbc.query("SELECT COALESCE(NULLIF(g.title, ''), p.name) FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE g.id = ?",
                (rs, i) -> rs.getString(1), groupBuyId).stream().findFirst().orElse("");
    }

    private static String titleOf(String name) {
        return name == null ? "" : name;
    }

    private record RefundRow(long participationId, long amount, String method) {
    }

    private Optional<RefundRow> refund(long refundId) {
        return jdbc.query("SELECT participation_id, amount, method FROM group_buy_refunds WHERE id = ?", (rs, i) -> new RefundRow(rs.getLong(1), rs.getLong(2),
                rs.getString(3)), refundId).stream().findFirst();
    }

    private void anomaly(Order o) {
        operators.publish(AdminNotificationEvent.COMMERCE_ORDER_ANOMALY, NotificationContext.empty().dedupe(Long.toString(o.id())).link("orderNo", o.orderNo()));
    }

    private void refundMail(Order o, String key, long amount, String method) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("orderNo", o.orderNo());
        v.put("groupBuyTitle", o.title());
        v.put("amount", amount(amount));
        v.put("refundMethod", text(new MessageRef("groupbuy.mail.refundMethod." + method)));
        v.put("orderLink", siteLink(orderPath(o)));
        outbox.enqueue(new OutboxMail(key, o.userId(), RequiredMail.GROUP_BUY_REFUND_COMPLETED, v));
    }

    private void notice(Order o, String key, Kind kind) {
        outbox.enqueue(new OutboxMail(key, o.userId(), RequiredMail.GROUP_BUY_ORDER_NOTICE,
                noticeVariables(o, text(new MessageRef("member.notification.GROUP_BUY.kind." + kind.name(), amount(o.amount()), o.orderNo())))));
    }

    private Map<String, Object> noticeVariables(Order o, String detail) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("orderNo", o.orderNo());
        v.put("groupBuyTitle", o.title());
        v.put("detail", detail);
        v.put("orderLink", siteLink(orderPath(o)));
        return v;
    }

    /** 계약 내용 통지의 변수(결정 57) — 주문 줄·금액·판매 조건·발송·청약철회 제한·판매자 */
    Map<String, Object> contractVariables(Order o) {
        Map<String, Object> g = jdbc.queryForMap("SELECT g.start_at, g.end_at, g.max_qty, g.max_qty_per_user, g.round_no, g.min_qty, g.fulfillment, g.ship_planned_at, "
                + "g.ship_within_business_days, p.withdrawal_restriction FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE g.id = ?", o.groupBuyId());
        List<String> lines = jdbc.query("SELECT s.name, i.qty FROM group_buy_participation_items i JOIN group_buy_skus s ON s.id = i.sku_id WHERE i.participation_id = ? "
                + "ORDER BY s.kind DESC, s.sort_order, i.id", (rs, i) -> rs.getString(1) + " × " + rs.getInt(2), o.id());
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("orderNo", o.orderNo());
        v.put("groupBuyTitle", o.title());
        v.put("items", String.join(", ", lines));
        v.put("amount", amount(o.amount()));
        v.put("shippingFee", amount(o.shippingFee()));
        Object perUser = g.get("max_qty_per_user");
        int minQty = ((Number) g.get("min_qty")).intValue();
        v.put("saleTerms", text(new MessageRef("groupbuy.mail.terms", day(g.get("start_at")), day(g.get("end_at")), g.get("max_qty"),
                perUser == null ? text(new MessageRef("groupbuy.mail.terms.noLimit")) : text(new MessageRef("groupbuy.mail.terms.perUser", perUser)), g.get("round_no"),
                minQty > 0 ? text(new MessageRef("groupbuy.mail.terms.threshold", minQty)) : text(new MessageRef("groupbuy.mail.terms.firstCome")))));
        v.put("shipPlan", g.get("ship_planned_at") != null ? text(new MessageRef("groupbuy.mail.shipPlanned", g.get("ship_planned_at").toString()))
                : text(new MessageRef("groupbuy.mail.shipWithin", g.get("ship_within_business_days"))));
        Object restriction = g.get("withdrawal_restriction");
        v.put("withdrawal", restriction == null || restriction.toString().isBlank() ? text(new MessageRef("groupbuy.mail.none")) : restriction.toString());
        BusinessInfo b = sales.businessInfo();
        v.put("seller", String.join(" · ", List.of(b.companyName(), b.representative(), b.businessNo(), b.mailOrderNo(), b.address(), b.phone(), b.email()).stream()
                .filter(s -> s != null && !s.isBlank()).toList()));
        v.put("orderLink", siteLink(orderPath(o)));
        return v;
    }

    private static String day(Object timestamp) {
        return timestamp instanceof Timestamp t ? LocalDate.ofInstant(t.toInstant(), ServiceTime.ZONE).toString() : "";
    }

    private String text(MessageRef ref) {
        return messages.get(ref, MAIL_LOCALE);
    }

    private static String amount(long value) {
        return String.format("%,d", value);
    }

    /** 메일 링크 — 사이트 안 경로(대기열 발송기가 사이트 공개 주소를 앞에 붙이지 않으므로 경로 그대로 두되, 사이트 주소를 알면 절대 주소) */
    private String siteLink(String path) {
        String base = jdbc.query("SELECT public_url FROM site_settings WHERE id = 1", (rs, i) -> rs.getString(1)).stream().findFirst().orElse(null);
        return base == null || base.isBlank() ? path : base.replaceAll("/+$", "") + path;
    }

    private static String orderPath(Order o) {
        return "/me/orders/" + o.orderNo();
    }

    private static String groupBuyPath(long groupBuyId) {
        return "/group-buys/" + groupBuyId;
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }
}

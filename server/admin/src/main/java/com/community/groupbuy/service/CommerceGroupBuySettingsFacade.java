package com.community.groupbuy.service;

import com.community.holiday.service.BusinessDayCalendar;
import com.community.holiday.service.SiteHolidayService;
import com.community.commerce.sales.BusinessInfo;
import com.community.commerce.sales.CommerceSalesService;
import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.CommerceGroupBuySettingsView;
import com.community.groupbuy.repository.master.CommerceGroupBuySettingsQueries;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.repository.master.ShippingPolicyStore;
import com.community.payment.repository.master.PaymentAdminQueries;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 「설정 › 커머스」의 공구 구획 다섯(배송비 정책·택배사·사업자 정보·판매 스위치·공구 설정)과 대시보드 커머스 칸(commerce.md 19·20절, 계약 결정 8·10·11·18). 업무는 shared 서비스가 한다.
 * 판매 켜기는 최고 관리자만(서비스가 403), 끄기는 최고 관리자 또는 {@code commerce.settings}(컨트롤러 입구).
 */
@Service
public class CommerceGroupBuySettingsFacade {

    /** 대시보드 「발송 뒤 N일 지난 SHIPPED」의 N(배송완료 처리 누락 — 결정 7) */
    public static final int DELIVERY_STALE_DAYS = 7;
    /** 대시보드 「곧 만료될 선점」 창 */
    public static final Duration HOLD_SOON = Duration.ofMinutes(2);

    private final CommerceGroupBuySettingsQueries queries;
    private final ShippingPolicyService policies;
    private final ShippingPolicyStore policyStore;
    private final FulfillmentService fulfillment;
    private final FulfillmentStore fulfillmentStore;
    private final CommerceSalesService sales;
    private final GroupBuySettingsService settings;
    private final GroupBuyShipDeadlines shipDeadlines;
    private final PaymentAdminQueries payments;
    private final ObjectProvider<Clock> clocks;
    private final SiteHolidayService siteHolidays;

    public CommerceGroupBuySettingsFacade(CommerceGroupBuySettingsQueries queries, ShippingPolicyService policies, ShippingPolicyStore policyStore, FulfillmentService fulfillment,
                                          FulfillmentStore fulfillmentStore, CommerceSalesService sales, GroupBuySettingsService settings,
                                          GroupBuyShipDeadlines shipDeadlines, PaymentAdminQueries payments, ObjectProvider<Clock> clocks,
                                          SiteHolidayService siteHolidays) {
        this.queries = queries;
        this.policies = policies;
        this.policyStore = policyStore;
        this.fulfillment = fulfillment;
        this.fulfillmentStore = fulfillmentStore;
        this.sales = sales;
        this.settings = settings;
        this.shipDeadlines = shipDeadlines;
        this.payments = payments;
        this.clocks = clocks;
        this.siteHolidays = siteHolidays;
    }

    // ── 배송비 정책 ─────────────────────────────────────────────────────────

    public CommerceGroupBuySettingsView.ShippingPolicies shippingPolicies() {
        Instant now = now();
        Map<Long, String> creators = policyStore.creators();
        Map<Long, Instant> created = policyStore.createdAt();
        List<CommerceGroupBuySettingsView.ShippingPolicyLine> items = policies.all().stream().map(r -> new CommerceGroupBuySettingsView.ShippingPolicyLine(
                Long.toString(r.id()), r.code(), r.name(), r.baseFee(), r.freeShippingOver(), r.returnFee(), r.returnFeeMode(), r.effectiveFrom(), r.effectiveTo(),
                !r.effectiveFrom().isAfter(now), !r.effectiveFrom().isAfter(now) && (r.effectiveTo() == null || r.effectiveTo().isAfter(now)),
                creators.getOrDefault(r.id(), ""), created.get(r.id()))).toList();
        return new CommerceGroupBuySettingsView.ShippingPolicies(items);
    }

    public CommerceGroupBuySettingsView.ShippingPolicies addShippingPolicy(AdminActor actor, GroupBuyAdminRequest.ShippingPolicy body) {
        GroupBuyAdminRequest.ShippingPolicy b = body == null ? new GroupBuyAdminRequest.ShippingPolicy(null, null, null, null, null, null, null) : body;
        policies.schedule(new ShippingPolicyService.PolicyInput(b.code(), b.name(), b.baseFee() == null ? -1 : b.baseFee(), b.freeShippingOver(),
                b.returnFee() == null ? -1 : b.returnFee(), b.returnFeeMode(), b.effectiveFrom()), actor.operatorId(), actor.ip());
        return shippingPolicies();
    }

    public CommerceGroupBuySettingsView.ShippingPolicies deleteShippingPolicy(AdminActor actor, String id) {
        policies.deleteScheduled(AdminIds.parse(id), actor.operatorId(), actor.ip());
        return shippingPolicies();
    }

    // ── 택배사 ──────────────────────────────────────────────────────────────

    public CommerceGroupBuySettingsView.Carriers carriers() {
        List<CommerceGroupBuySettingsView.CarrierLine> items = fulfillment.carriers().stream().map(c -> new CommerceGroupBuySettingsView.CarrierLine(c.code(), c.name(),
                c.trackingUrl(), c.enabled(), fulfillmentStore.carrierInUse(c.code()))).toList();
        CommerceGroupBuySettingsQueries.CarrierEdit last = queries.lastCarrierEdit().orElse(null);
        return new CommerceGroupBuySettingsView.Carriers(items, last == null ? null : last.updatedAt(), last == null ? null : last.operatorName());
    }

    public CommerceGroupBuySettingsView.Carriers saveCarriers(AdminActor actor, GroupBuyAdminRequest.Carriers body) {
        List<FulfillmentStore.CarrierRow> rows = new ArrayList<>();
        List<GroupBuyAdminRequest.Carrier> in = body == null || body.items() == null ? List.of() : body.items();
        for (int i = 0; i < in.size(); i++) {
            GroupBuyAdminRequest.Carrier c = in.get(i);
            rows.add(new FulfillmentStore.CarrierRow(c.code(), c.name(), c.trackingUrlTemplate(), !Boolean.FALSE.equals(c.active()), i));
        }
        fulfillment.saveCarriers(rows, actor.operatorId(), actor.ip());
        return carriers();
    }

    // ── 사업자 정보·판매 스위치 ─────────────────────────────────────────────

    public CommerceGroupBuySettingsView.BusinessInfo businessInfo() {
        return view(sales.businessInfo());
    }

    public CommerceGroupBuySettingsView.BusinessInfo saveBusinessInfo(AdminActor actor, GroupBuyAdminRequest.BusinessInfo body) {
        GroupBuyAdminRequest.BusinessValues v = body == null || body.values() == null
                ? new GroupBuyAdminRequest.BusinessValues(null, null, null, null, null, null, null, null, null, null, null, null) : body.values();
        return view(sales.saveBusinessInfo(new BusinessInfo(v.companyName(), v.representative(), v.businessNo(), v.mailOrderNo(), v.mailOrderAuthority(), v.address(),
                v.phone(), v.email(), v.businessHours(), v.privacyOfficer(), v.hostingProvider(), v.shippingRefundPolicy(), null, null), actor.operatorId(), actor.ip()));
    }

    public CommerceGroupBuySettingsView.Sales salesState() {
        return view(sales.state());
    }

    public CommerceGroupBuySettingsView.Sales check(AdminActor actor, String key, GroupBuyAdminRequest.Checklist body) {
        return view(sales.check(key, body != null && Boolean.TRUE.equals(body.checked()), actor.operatorId(), actor.ip()));
    }

    /** 켜기는 최고 관리자만(서비스가 403 {@code COMMERCE_SALES_ROOT_ONLY}), 끄기는 최고 관리자 또는 {@code commerce.settings} */
    public CommerceGroupBuySettingsView.Sales toggle(AdminActor actor, GroupBuyAdminRequest.SalesToggle body) {
        boolean enabled = body != null && Boolean.TRUE.equals(body.enabled());
        if (!enabled && !actor.root() && !actor.can(Permission.COMMERCE_SETTINGS)) {
            throw new CommonException(ErrorCode.FORBIDDEN);
        }
        return view(sales.toggle(enabled, body == null ? null : body.reason(), actor.operatorId(), actor.root(), actor.ip()));
    }

    // ── 공구 설정 ───────────────────────────────────────────────────────────

    public CommerceGroupBuySettingsView.Settings groupBuySettings() {
        return view(settings.load());
    }

    public CommerceGroupBuySettingsView.Settings saveGroupBuySettings(AdminActor actor, GroupBuyAdminRequest.Settings body) {
        GroupBuyAdminRequest.SettingsValues v = body == null || body.values() == null
                ? new GroupBuyAdminRequest.SettingsValues(null, null, null, null, null, null, null, null, null, null, null, null) : body.values();
        Map<String, Long> numbers = new LinkedHashMap<>();
        numbers.put("holdMinutes", v.holdMinutes());
        numbers.put("thresholdPeriodMaxDays", v.thresholdPeriodMaxDays());
        numbers.put("firstComePeriodMaxDays", v.firstComePeriodMaxDays());
        numbers.put("extensionMaxMinutes", v.extensionMaxMinutes());
        numbers.put("extensionWindowMinutes", v.extensionWindowMinutes());
        numbers.put("judgeGraceMinutes", v.judgeGraceMinutes());
        numbers.put("compensationCap", v.compensationCap());
        numbers.put("largeRefundThreshold", v.largeRefundThreshold());
        numbers.put("addressRevealMinutes", v.addressRevealMinutes());
        numbers.put("expiredAddressRetentionDays", v.expiredAddressRetentionDays());
        numbers.put("shipWithinBusinessDays", v.shipWithinBusinessDays());
        numbers.put("supplyChoiceBusinessDays", v.supplyChoiceBusinessDays());
        return view(settings.save(numbers, actor.operatorId(), actor.ip()));
    }

    // ── 대시보드 ────────────────────────────────────────────────────────────

    /** 칸마다 볼 권한(주문 = commerce.order.read, 결제 이상 = commerce.payment.read, 수량 = commerce.product.read)이 없으면 null */
    public CommerceGroupBuySettingsView.Dashboard dashboard(AdminActor actor) {
        Instant now = now();
        boolean orders = actor.root() || actor.can(Permission.COMMERCE_ORDER_READ);
        boolean stock = actor.root() || actor.can(Permission.COMMERCE_PRODUCT_READ);
        boolean pay = actor.root() || actor.can(Permission.COMMERCE_PAYMENT_READ);
        Long preparing = orders ? queries.confirmedOrders() : null;
        CommerceGroupBuySettingsView.OpenRequests open = null;
        if (orders) {
            // 기한 단계(GroupBuyBusinessDays.deadlineLevel — 3영업일 심각, 2영업일 경고)를 단계마다 시작 시각 경계로 DB 에서 센다(예전에는 열린 요청을 다 읽어 자바로 셌다)
            BusinessDayCalendar holidays = siteHolidays.calendar();
            LocalDate today = ServiceTime.dateOf(now);
            long total = queries.openRequests();
            long critical = queries.openRequestsStartedBefore(GroupBuyBusinessDays.elapsedCutoff(today, 3, holidays).atStartOfDay(ServiceTime.ZONE).toInstant());
            long warn = queries.openRequestsStartedBefore(GroupBuyBusinessDays.elapsedCutoff(today, 2, holidays).atStartOfDay(ServiceTime.ZONE).toInstant()) - critical;
            open = new CommerceGroupBuySettingsView.OpenRequests(total, warn, critical);
        }
        Long anomalies = pay ? payments.openAnomalyCount() : null;
        Long holds = orders ? queries.holdsEndingBy(now, now.plus(HOLD_SOON)) : null;
        Long lowStock = stock ? queries.lowStockGroupBuys() : null;
        Long overdue = orders ? (long) shipDeadlines.overdue(now, 100_000).size() : null;
        CommerceGroupBuySettingsView.DeliveryStale stale = orders ? new CommerceGroupBuySettingsView.DeliveryStale(
                queries.shippedBefore(now.minus(Duration.ofDays(DELIVERY_STALE_DAYS))), DELIVERY_STALE_DAYS) : null;
        return new CommerceGroupBuySettingsView.Dashboard(preparing, open, anomalies, holds, lowStock, overdue, stale, now);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private static CommerceGroupBuySettingsView.BusinessInfo view(BusinessInfo b) {
        return new CommerceGroupBuySettingsView.BusinessInfo(new CommerceGroupBuySettingsView.BusinessValues(b.companyName(), b.representative(), b.businessNo(),
                b.mailOrderNo(), b.mailOrderAuthority(), b.address(), b.phone(), b.email(), b.businessHours(), b.privacyOfficer(), b.hostingProvider(),
                b.shippingRefundPolicy()), b.ready(), b.updatedAt(), b.updatedBy());
    }

    private static CommerceGroupBuySettingsView.Sales view(CommerceSalesService.SalesState s) {
        return new CommerceGroupBuySettingsView.Sales(s.enabled(), s.autoChecks().stream().map(c -> new CommerceGroupBuySettingsView.AutoCheck(c.key(), c.ok())).toList(),
                s.checklist().stream().map(c -> new CommerceGroupBuySettingsView.ChecklistItem(c.key(), c.checked(), c.checkedBy(), c.checkedAt())).toList(), s.canEnable(),
                s.changedAt(), s.changedBy());
    }

    private static CommerceGroupBuySettingsView.Settings view(GroupBuySettingsService.Values v) {
        Map<String, Long> n = v.numbers();
        Map<String, Long> defaults = new LinkedHashMap<>();
        Map<String, CommerceGroupBuySettingsView.Range> ranges = new LinkedHashMap<>();
        GroupBuySettingsService.FIELDS.forEach(f -> {
            defaults.put(f.name(), f.defaultValue());
            ranges.put(f.name(), new CommerceGroupBuySettingsView.Range(f.min(), f.max()));
        });
        return new CommerceGroupBuySettingsView.Settings(new CommerceGroupBuySettingsView.SettingsValues(n.get("holdMinutes"), n.get("thresholdPeriodMaxDays"),
                n.get("firstComePeriodMaxDays"), n.get("extensionMaxMinutes"), n.get("extensionWindowMinutes"), n.get("judgeGraceMinutes"), n.get("compensationCap"),
                n.get("largeRefundThreshold"), n.get("addressRevealMinutes"), n.get("expiredAddressRetentionDays"), n.get("shipWithinBusinessDays"),
                n.get("supplyChoiceBusinessDays")), defaults, ranges, v.updatedAt(), v.updatedBy());
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }
}

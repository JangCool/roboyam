package com.community.bizapplication.service;

import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizCreativeRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizApplicationReader.Bucket;
import com.community.bizapplication.service.BizApplicationReader.Snapshot;
import com.community.bizapplication.service.BizApplicationReader.Summary;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.response.PaymentCheckoutView;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 사업자 센터 신청 건 API 의 응답 만들기(user-web {@code useBusinessApi} 모양) — 처리는 shared {@link BizApplicationMemberService}, 읽기는 {@link BizApplicationReader}.
 * 날짜는 반열림 순간 ↔ 한국 날짜(끝 포함)로 바꾼다.
 */
@Service
public class MyBizApplicationService {

    private final BizApplicationMemberService members;
    private final BizApplicationReader reader;
    private final BizApplicationProperties props;
    private final Messages messages;

    public MyBizApplicationService(BizApplicationMemberService members, BizApplicationReader reader, BizApplicationProperties props, Messages messages) {
        this.members = members;
        this.reader = reader;
        this.props = props;
        this.messages = messages;
    }

    // ── 상품·남은 칸·링크 ──────────────────────────────────────────────────

    public List<BizMemberView.AdProduct> adProducts() {
        return members.adProducts().stream().map(p -> new BizMemberView.AdProduct(p.rule().product().publicName(), p.rule().unitPrice(), p.rule().maxDays(),
                p.rule().maxLeadDays(), p.rule().capacity() == null ? 0 : p.rule().capacity(), p.openDays(), p.rule().accepting())).toList();
    }

    public BizMemberView.DealProduct dealProduct() {
        Map<BizProduct, PriceRule> all = members.priceRules();
        List<BizMemberView.DealPlacement> placements = List.of(BizProduct.HOTDEAL_TOP, BizProduct.HOTDEAL_CATEGORY).stream().map(all::get)
                .map(r -> new BizMemberView.DealPlacement(r.product().publicName(), r.unitPrice(), r.capacity() == null ? 0 : r.capacity(), r.maxDays())).toList();
        return new BizMemberView.DealProduct(all.get(BizProduct.HOTDEAL_POST).unitPrice(), placements);
    }

    public List<BizMemberView.AvailabilityDay> availability(String slot, String from, String to) {
        return members.availability(slot, from, to).stream().map(d -> new BizMemberView.AvailabilityDay(d.date().toString(), d.remaining(), d.capacity())).toList();
    }

    public BizMemberView.DealLinkCheck checkLink(String url) {
        BizDealLinks.Check check = BizDealLinks.check(url)
                .orElseThrow(() -> ValidationException.of("linkUrl", new MessageRef("bizapp.link.invalid")));
        return new BizMemberView.DealLinkCheck(check.host(), check.shopName(), check.blocked(),
                check.blockedReason() == null ? null : messages.get(new MessageRef(check.blockedReason())), check.affiliate());
    }

    // ── 신청·처리 ───────────────────────────────────────────────────────────

    public BizMemberView.Created submitAd(long userId, BizAdApplyRequest body) {
        return new BizMemberView.Created(Long.toString(members.submitAd(userId, body)));
    }

    public BizMemberView.Created submitDeal(long userId, BizDealApplyRequest body) {
        return new BizMemberView.Created(Long.toString(members.submitDeal(userId, body)));
    }

    public BizMemberView.Detail cancel(long userId, long id) {
        members.cancel(userId, id);
        return detail(userId, id);
    }

    public BizMemberView.Detail changeCreative(long userId, long id, BizCreativeRequest body) {
        members.changeCreative(userId, id, body);
        return detail(userId, id);
    }

    public BizMemberView.PaymentStart startPayment(long userId, long id) {
        PaymentCheckout checkout = members.startPayment(userId, id);
        return new BizMemberView.PaymentStart("BIZ_APPLICATION", Long.toString(id), checkout.expiresAt(), PaymentCheckoutView.of(checkout));
    }

    public BizMemberView.Detail requestRefund(long userId, long id, String reason) {
        members.requestRefund(userId, id, reason);
        return detail(userId, id);
    }

    // ── 읽기 ───────────────────────────────────────────────────────────────

    /** 내 신청 건 — 묶음 거르기(ALL·REVIEW·AWAITING_PAYMENT·AWAITING_DEPOSIT·UPCOMING·RUNNING·ENDED·CLOSED), 묶음별 건수 */
    public BizMemberView.Page list(long userId, String bucketText) {
        String bucket = bucketText == null || bucketText.isBlank() ? "ALL" : bucketText.strip();
        if (!bucket.equals("ALL") && Arrays.stream(Bucket.values()).noneMatch(b -> b.name().equals(bucket))) {
            throw ValidationException.of("bucket", new MessageRef("bizapp.value.invalid"));
        }
        Instant now = reader.now();
        List<AppRow> apps = reader.byUser(userId);
        Map<Long, CreativeRow> creatives = reader.creatives(apps.stream().map(AppRow::id).toList());
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", (long) apps.size());
        for (Bucket b : Bucket.values()) {
            counts.put(b.name(), 0L);
        }
        List<BizMemberView.Summary> items = new java.util.ArrayList<>();
        for (Summary s : reader.summaries(apps)) {
            Bucket b = BizApplicationReader.bucket(s.app(), now);
            counts.merge(b.name(), 1L, Long::sum);
            if (bucket.equals("ALL") || bucket.equals(b.name())) {
                items.add(summary(s.app(), s.items(), creatives.get(s.app().id()), s.refund(), now));
            }
        }
        return new BizMemberView.Page(items, items.size(), counts);
    }

    public BizMemberView.Detail detail(long userId, long id) {
        Snapshot s = reader.snapshotOwned(id, userId);
        Instant now = reader.now();
        AppRow a = s.app();
        BizMemberView.Summary sum = summary(a, s.items(), s.creative(), s.refund(), now);
        Map<String, Object> c = s.creative() == null ? Map.of() : s.creative().content();
        BizMemberView.Creative creative = a.kind().ad() ? new BizMemberView.Creative(s.creative() == null ? 1 : s.creative().version(), str(c.get("imageUrl")),
                str(c.get("imageUrl")) == null ? null : fileName(str(c.get("imageUrl"))), str(c.get("linkUrl")), str(c.get("altText")), str(c.get("text"))) : null;
        BizDealInfo deal = a.kind() == BizApplicationKind.HOTDEAL_POST ? new BizDealInfo(str(c.get("linkUrl")), str(c.get("shopName")), num(c.get("listPrice")),
                num(c.get("dealPrice")), num(c.get("shippingFee")), str(c.get("endsOn")), str(c.get("stock")), str(c.get("body")), str(c.get("placement"))) : null;
        BizMemberView.Deposit deposit = null;
        if (a.paymentMethod() == BizPaymentMethod.MANUAL) {
            BizApplicationProperties.DepositAccount acc = props.depositAccount();
            deposit = new BizMemberView.Deposit(blankToNull(acc.bankName()), blankToNull(acc.accountNo()), blankToNull(acc.accountHolder()), a.businessName(),
                    s.contracts().stream().anyMatch(x -> x.active()) ? "RECEIVED" : "WAITING", s.deposit() != null);
        }
        RefundRow r = s.refund();
        BizMemberView.Refund refund = r == null ? null : new BizMemberView.Refund(r.type() == BizRefundType.FULL_AUTO ? "FULL_AUTO" : "PRORATED_REVIEW", r.status().name(),
                r.reason(), r.requestedAt(), r.proposedAmount(), r.approvedAmount(), r.decisionReason());
        List<BizMemberView.History> history = s.events().stream().filter(e -> publicAction(e.action()))
                .map(e -> new BizMemberView.History(e.at(), actor(e.actorType()), e.action(), e.note())).toList();
        return new BizMemberView.Detail(sum.id(), sum.kind(), sum.title(), sum.adType(), sum.placement(), sum.startDate(), sum.endDate(), sum.status(), sum.phase(),
                sum.paymentMethod(), sum.amount(), sum.amountConfirmed(), sum.paymentDueAt(), sum.depositDueAt(), sum.submittedAt(), sum.refundRequested(),
                amount(a.quote()), a.approved() == null ? null : amount(a.approved()), a.adjustReason(), creative, deal,
                a.rejectCode() == null ? null : a.rejectCode().name(), a.rejectText(), a.takedownText(), deposit, refund, history);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private BizMemberView.Summary summary(AppRow a, List<ItemRow> items, CreativeRow creative, RefundRow refund, Instant now) {
        BizProduct capacity = BizApplicationReader.capacityProduct(items);
        String adType = a.kind().ad() && capacity != null ? capacity.publicName() : null;
        String placement = null;
        if (a.kind() == BizApplicationKind.HOTDEAL_POST) {
            Object p = creative == null ? null : creative.content().get("placement");
            placement = p == null ? (capacity == null ? "NONE" : capacity.publicName()) : p.toString();
        } else if (a.kind() == BizApplicationKind.HOTDEAL_PLACEMENT && capacity != null) {
            placement = capacity.publicName();
        }
        BizPhase phase = BizApplicationReader.phase(a, now);
        Amount shown = a.approved() != null ? a.approved() : a.quote();
        return new BizMemberView.Summary(Long.toString(a.id()), a.kind().name(), a.title(), adType, placement, BizPeriods.startDate(a.startAt()).toString(),
                BizPeriods.endDate(a.endAt()).toString(), a.status().name(), phase == null ? null : phase.name(),
                (a.paymentMethod() != null ? a.paymentMethod() : a.preferredMethod()).name(), amount(shown), a.approved() != null, a.paymentDeadlineAt(),
                a.depositDeadlineAt(), a.createdAt(), refund != null);
    }

    private static boolean publicAction(String action) {
        try {
            return BizEventAction.valueOf(action).publicVisible();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String actor(BizActorType type) {
        return switch (type) {
            case MEMBER -> "ME";
            case OPERATOR -> "OPERATOR";
            case SYSTEM -> "SYSTEM";
        };
    }

    private static BizMemberView.Amount amount(Amount a) {
        return new BizMemberView.Amount(a.supply(), a.vat(), a.total());
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long num(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static String fileName(String url) {
        int slash = url.lastIndexOf('/');
        return slash < 0 ? url : url.substring(slash + 1);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

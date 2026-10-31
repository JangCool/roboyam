package com.community.bizapplication.service;

import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizCreativeRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.BizSource;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AdImage;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizApplicationStore.NewApp;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.service.BizPeriods.Period;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.service.BusinessEligibility;
import com.community.business.service.BusinessEligibility.ApprovedBusiness;
import com.community.media.service.ImageStorage;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.service.PaymentOrderService;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사업자 쪽 신청 건 처리(user-web {@code /business/**}) — 광고·핫딜 심사 신청 · 취소 · 소재 바꾸기 · 온라인 결제 시작 · 취소·환불 요청과 상품·남은 칸 안내.
 * 자격은 <b>반드시</b> {@link BusinessEligibility#requireApproved} 한 곳(409 {@code BUSINESS_NOT_APPROVED}·{@code BUSINESS_RE_REVIEW}). 다른 회원의 건은 404.
 * 클래스 트랜잭션을 두지 않는다(검사가 DB 연결을 붙잡지 않게) — 쓰기는 {@link TransactionTemplate}.
 */
@Service
public class BizApplicationMemberService {

    /** 열린 결제가 이만큼 남았으면 그 결제창을 다시 준다 */
    static final Duration REUSE_CHECKOUT = Duration.ofMinutes(1);
    /** 결제 주문 유효 시간 상한(결제 모듈 1~60분) */
    static final Duration CHECKOUT_MAX = Duration.ofMinutes(30);
    /** 핫딜 글(노출 없음) 기본 게시 기간(가정) */
    static final int DEAL_DEFAULT_DAYS = 7;

    /** 상품 안내 한 줄 — 앞으로 30일 중 빈 날 수 */
    public record ProductInfo(PriceRule rule, int openDays) {
    }

    private final BizApplicationStore store;
    private final BizPriceRuleStore rules;
    private final BizRefundStore refunds;
    private final BizSlotService slots;
    private final BizApplicationOps ops;
    private final BusinessEligibility eligibility;
    private final PaymentOrderService payments;
    private final ObjectProvider<ImageStorage> images;
    private final TransactionTemplate tx;

    public BizApplicationMemberService(BizApplicationStore store, BizPriceRuleStore rules, BizRefundStore refunds, BizSlotService slots, BizApplicationOps ops,
                                       BusinessEligibility eligibility, PaymentOrderService payments, ObjectProvider<ImageStorage> images,
                                       PlatformTransactionManager transactions) {
        this.store = store;
        this.rules = rules;
        this.refunds = refunds;
        this.slots = slots;
        this.ops = ops;
        this.eligibility = eligibility;
        this.payments = payments;
        this.images = images;
        this.tx = new TransactionTemplate(transactions);
    }

    // ── 상품·남은 칸 ───────────────────────────────────────────────────────

    /** 광고 종류(배너 넷 + 한 줄) — 앞으로 30일(내일부터) 중 빈 날 수 */
    public List<ProductInfo> adProducts() {
        Map<BizProduct, PriceRule> all = rules.all();
        LocalDate from = today().plusDays(1);
        List<ProductInfo> out = new ArrayList<>();
        for (BizProduct p : List.of(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER, BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER, BizProduct.TEXT_LINE)) {
            PriceRule rule = all.get(p);
            int open = (int) slots.daily(p, from, from.plusDays(29)).stream().filter(d -> d.remaining() > 0).count();
            out.add(new ProductInfo(rule, open));
        }
        return out;
    }

    public Map<BizProduct, PriceRule> priceRules() {
        return rules.all();
    }

    /** 날짜별 남은 칸 — 92일까지 */
    public List<BizSlotService.DayRoom> availability(String slot, String fromText, String toText) {
        BizFields f = BizFields.start();
        BizProduct product = f.oneOf("slot", slot, s -> BizProduct.ofPublic(s).orElse(null));
        LocalDate from = f.date("from", fromText);
        LocalDate to = f.date("to", toText);
        if (from != null && to != null && (to.isBefore(from) || to.isAfter(from.plusDays(91)))) {
            f.add("to", "bizapp.period.range", 92);
        }
        f.throwIfAny();
        return slots.daily(product, from, to);
    }

    // ── 신청 ───────────────────────────────────────────────────────────────

    /** 광고 심사 신청 — 결제는 승인 뒤. 돌려준 값 = 신청 건 ID */
    public long submitAd(long userId, BizAdApplyRequest body) {
        BizAdApplyRequest b = body == null ? new BizAdApplyRequest(null, null, null, null, null, null, null, null, null, null) : body;
        return tx.execute(status -> {
            ApprovedBusiness biz = eligibility.requireApproved(userId, BusinessServiceKind.AD);
            Map<BizProduct, PriceRule> all = rules.all();
            BizFields f = BizFields.start();
            BizProduct product = f.oneOf("adType", b.adType(), s -> BizProduct.ofPublic(s).filter(BizProduct::adProduct).orElse(null));
            Period period = product == null ? null : publicPeriod(f, "period", b.startDate(), b.endDate(), all.get(product));
            Map<String, Object> content = new LinkedHashMap<>();
            String title;
            AdImage image = null;
            if (product == null || product.banner()) {
                Long fileId = BizFields.id(b.imageFileId());
                image = fileId == null ? null : store.adImage(fileId, userId).orElse(null);
                if (image == null) {
                    f.add("imageFileId", BizFields.strip(b.imageFileId()) == null ? "bizapp.field.required" : "bizapp.image.notFound");
                }
                String link = f.link("linkUrl", b.linkUrl(), true);
                String alt = f.text("altText", b.altText(), 100);
                title = alt;
                if (image != null) {
                    content.put("imageFileId", Long.toString(image.id()));
                    content.put("imageUrl", imageUrl(image.storageKey()));
                }
                content.put("linkUrl", link);
                content.put("altText", alt);
            } else {
                String text = f.text("text", b.text(), 40);
                String link = f.link("linkUrl", b.linkUrl(), false);
                title = text;
                content.put("text", text);
                content.put("linkUrl", link);
            }
            BizPaymentMethod method = f.oneOf("paymentMethod", b.paymentMethod(), BizPaymentMethod::valueOf);
            f.agree(b.agreedAdPolicyVersion(), b.agreedRefundPolicyVersion());
            f.throwIfAny();
            PriceRule rule = all.get(product);
            if (!rule.accepting()) {
                throw BizApplicationException.productClosed();
            }
            slots.require(product, period.start(), period.end(), null);
            BizApplicationKind kind = product.banner() ? BizApplicationKind.AD_BANNER : BizApplicationKind.AD_TEXT_LINE;
            long id = create(userId, biz, kind, title, period, method, List.of(product), b.agreedAdPolicyVersion(), b.agreedRefundPolicyVersion(), content);
            if (image != null) {
                store.markImageUsed(image.id(), ops.now());
            }
            return id;
        });
    }

    /** 사업자 핫딜 심사 신청 — 노출을 고르면 같은 신청 건(HOTDEAL_POST + 노출 줄) */
    public long submitDeal(long userId, BizDealApplyRequest body) {
        BizDealApplyRequest b = body == null ? new BizDealApplyRequest(null, null, null, null, null, null, null) : body;
        return tx.execute(status -> {
            ApprovedBusiness biz = eligibility.requireApproved(userId, BusinessServiceKind.HOTDEAL);
            Map<BizProduct, PriceRule> all = rules.all();
            BizFields f = BizFields.start();
            String title = f.text("title", b.title(), 100);
            Map<String, Object> content = dealContent(f, title, b.deal());
            BizProduct placement = placementOf(b.deal());
            Period period;
            List<BizProduct> products = new ArrayList<>(List.of(BizProduct.HOTDEAL_POST));
            if (placement != null) {
                products.add(placement);
                period = publicPeriod(f, "period", b.placementStartDate(), b.placementEndDate(), all.get(placement));
            } else {
                period = defaultDealPeriod(content.get("endsOn") instanceof String e ? LocalDate.parse(e) : null, all.get(BizProduct.HOTDEAL_POST));
            }
            BizPaymentMethod method = f.oneOf("paymentMethod", b.paymentMethod(), BizPaymentMethod::valueOf);
            f.agree(b.agreedAdPolicyVersion(), b.agreedRefundPolicyVersion());
            f.throwIfAny();
            for (BizProduct p : products) {
                if (!all.get(p).accepting()) {
                    throw BizApplicationException.productClosed();
                }
            }
            if (placement != null) {
                slots.require(placement, period.start(), period.end(), null);
            }
            return create(userId, biz, BizApplicationKind.HOTDEAL_POST, title, period, method, products, b.agreedAdPolicyVersion(), b.agreedRefundPolicyVersion(),
                    content);
        });
    }

    private long create(long userId, ApprovedBusiness biz, BizApplicationKind kind, String title, Period period, BizPaymentMethod method, List<BizProduct> products,
                        String adPolicy, String refundPolicy, Map<String, Object> content) {
        Instant now = ops.now();
        long id = ops.nextId();
        List<ItemRow> items = items(id, products, period);
        Amount quote = BizPricing.amountOf(items.stream().mapToLong(ItemRow::supply).sum());
        store.insert(new NewApp(id, kind, BizSource.BUSINESS, biz.profileId(), userId, null, biz.companyName(), biz.bizNoLast4(), title, period.start(), period.end(),
                method, quote, adPolicy.strip(), refundPolicy.strip(), now, now));
        for (ItemRow item : items) {
            store.insertItem(item, now);
        }
        store.insertCreative(ops.nextId(), id, 1, content, BizActorType.MEMBER, userId, now);
        ops.event(id, BizEventAction.SUBMITTED, BizActorType.MEMBER, userId, null, now);
        AppRow app = store.find(id).orElseThrow();
        ops.notices().member(app, BizApplicationNotices.Kind.SUBMITTED, quote.total(), null);
        ops.notices().reviewPending(app);
        return id;
    }

    /** 자리·금액 줄 — 요금표로 다시 계산(승인 때 기간이 바뀌어도 같은 식) */
    List<ItemRow> items(long applicationId, List<BizProduct> products, Period period) {
        Map<BizProduct, PriceRule> all = rules.all();
        List<ItemRow> out = new ArrayList<>();
        for (BizProduct p : products) {
            PriceRule rule = all.get(p);
            int units = rule.perPost() ? 1 : BizPricing.days(period.start(), period.end());
            out.add(new ItemRow(ops.nextId(), applicationId, p, rule.unitPrice(), units, rule.unitPrice() * units, period.start(), period.end()));
        }
        return out;
    }

    // ── 취소·소재·결제·환불 ───────────────────────────────────────────────

    /** 결제 전 취소 — 심사 대기·결제 대기·입금 대기(입금 기록 전). 입금 기록이 있으면 409 */
    public void cancel(long userId, long id) {
        tx.executeWithoutResult(status -> {
            AppRow app = ops.closePaymentsThenLock(id, userId, "BIZ_MEMBER_CANCEL");
            BizTransitions.require(Action.MEMBER_CANCEL, app.status());
            if (app.status() == BizApplicationStatus.AWAITING_DEPOSIT && store.deposit(id).isPresent()) {
                throw BizApplicationException.stateChanged();
            }
            Instant now = ops.now();
            ops.move(app, Action.MEMBER_CANCEL, now);
            ops.event(id, BizEventAction.CANCELED, BizActorType.MEMBER, userId, null, now);
        });
    }

    /**
     * 소재 바꾸기 — 심사 대기면 새 버전만, 승인 뒤 결제 전(입금 기록 없음)이면 새 버전 + 다시 심사 대기(승인 스냅샷 비움 · 자리 반납 · 열린 결제 닫기, 사업자 알림 없음).
     * 그 밖 409. 자격도 다시 본다(재심사 중이면 409 {@code BUSINESS_RE_REVIEW}).
     */
    public void changeCreative(long userId, long id, BizCreativeRequest body) {
        BizCreativeRequest b = body == null ? new BizCreativeRequest(null, null, null, null, null, null) : body;
        tx.executeWithoutResult(status -> {
            AppRow app = ops.closePaymentsThenLock(id, userId, "BIZ_CREATIVE_CHANGE");
            BizTransitions.require(Action.CREATIVE_CHANGE, app.status());
            if (app.status() == BizApplicationStatus.AWAITING_DEPOSIT && store.deposit(id).isPresent()) {
                throw BizApplicationException.stateChanged();
            }
            eligibility.requireApproved(userId, app.kind().service());
            CreativeRow current = store.latestCreative(id).orElseThrow();
            BizFields f = BizFields.start();
            Map<String, Object> content = new LinkedHashMap<>();
            String title;
            AdImage image = null;
            switch (app.kind()) {
                case AD_BANNER -> {
                    // 이미지를 다시 올리지 않으면(imageFileId 없음) 지금 버전의 이미지를 그대로 쓴다
                    String fileText = BizFields.strip(b.imageFileId()) != null ? b.imageFileId()
                            : current.content().get("imageFileId") == null ? null : current.content().get("imageFileId").toString();
                    Long fileId = BizFields.id(fileText);
                    image = fileId == null ? null : store.adImage(fileId, userId).orElse(null);
                    if (image == null) {
                        f.add("imageFileId", BizFields.strip(fileText) == null ? "bizapp.field.required" : "bizapp.image.notFound");
                    }
                    String link = f.link("linkUrl", b.linkUrl(), true);
                    title = f.text("altText", b.altText(), 100);
                    if (image != null) {
                        content.put("imageFileId", Long.toString(image.id()));
                        content.put("imageUrl", imageUrl(image.storageKey()));
                    }
                    content.put("linkUrl", link);
                    content.put("altText", title);
                }
                case AD_TEXT_LINE -> {
                    title = f.text("text", b.text(), 40);
                    String link = f.link("linkUrl", b.linkUrl(), false);
                    content.put("text", title);
                    content.put("linkUrl", link);
                }
                case HOTDEAL_POST -> {
                    title = f.text("title", b.title(), 100);
                    content = dealContent(f, title, b.deal());
                    Object before = current.content().get("placement");
                    if (!f.has("deal.placement") && !String.valueOf(before).equals(String.valueOf(content.get("placement")))) {
                        f.add("deal.placement", "bizapp.placement.fixed");
                    }
                }
                default -> throw BizApplicationException.stateChanged(); // 게시된 핫딜 노출은 사업자 신청 화면이 없다(핫딜 게시 세션)
            }
            f.throwIfAny();
            Instant now = ops.now();
            if (app.status() == BizApplicationStatus.SUBMITTED) {
                store.retitle(id, title, now);
            } else if (!store.backToReview(id, app.status(), title, now)) {
                throw BizApplicationException.stateChanged();
            }
            store.insertCreative(ops.nextId(), id, current.version() + 1, content, BizActorType.MEMBER, userId, now);
            if (image != null) {
                store.markImageUsed(image.id(), now);
            }
            ops.event(id, BizEventAction.CREATIVE_CHANGED, BizActorType.MEMBER, userId, app.status() == BizApplicationStatus.SUBMITTED ? null : "BACK_TO_REVIEW", now);
            ops.notices().reviewPending(app);
        });
    }

    /**
     * 온라인 결제 시작 — 결제 대기·온라인·결제 기한 전만. 열린 결제가 1분 이상 남았으면 그것, 아니면 새 결제(유효 시간 = min(30분, 결제 기한 − 지금), 1분 미만이면 409).
     * 결제 주문을 먼저 만들고(결제 행) 그다음 신청 건 행을 잠가 상태를 다시 본다 — 닫는 길과 같은 잠금 차례.
     */
    public PaymentCheckout startPayment(long userId, long id) {
        return tx.execute(status -> {
            AppRow seen = store.findOwned(id, userId).orElseThrow(BizApplicationException::notFound);
            Instant now = ops.now();
            if (seen.status() != BizApplicationStatus.AWAITING_PAYMENT || seen.paymentMethod() != BizPaymentMethod.ONLINE || seen.paymentDeadlineAt() == null
                    || !seen.paymentDeadlineAt().isAfter(now)) {
                throw BizApplicationException.stateChanged();
            }
            Optional<PaymentCheckout> open = payments.openCheckout(PaymentOrderType.BIZ_APPLICATION, id, REUSE_CHECKOUT);
            PaymentCheckout checkout;
            if (open.isPresent()) {
                checkout = open.get();
            } else {
                Duration left = Duration.between(now, seen.paymentDeadlineAt());
                Duration valid = left.compareTo(CHECKOUT_MAX) > 0 ? CHECKOUT_MAX : left;
                if (valid.compareTo(Duration.ofMinutes(1)) < 0) {
                    throw BizApplicationException.stateChanged();
                }
                valid = Duration.ofSeconds(valid.toSeconds());
                checkout = payments.create(new PaymentOrderRequest(userId, PaymentOrderType.BIZ_APPLICATION, id, orderName(seen), seen.approved().total(), valid));
            }
            AppRow locked = store.lockOwned(id, userId).orElseThrow(BizApplicationException::notFound);
            if (locked.status() != BizApplicationStatus.AWAITING_PAYMENT || locked.paymentMethod() != BizPaymentMethod.ONLINE
                    || locked.approved().total() != checkout.amount()) {
                throw BizApplicationException.stateChanged();
            }
            return checkout;
        });
    }

    /**
     * 취소·환불 요청(H1) — 예약만, 환불 줄이 한 번도 없을 때만(거절된 뒤 다시 요청도 막는다 — 가정). 시작 전 = 전액 자동(취소 + 온라인 PG 취소 / 수동 송금 대기),
     * 진행 중 = 일할 심사 대기(제안액, 상태 그대로), 끝남 = 409.
     */
    public void requestRefund(long userId, long id, String reasonText) {
        BizFields f = BizFields.start();
        String reason = f.text("reason", reasonText, 300);
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lockOwned(id, userId).orElseThrow(BizApplicationException::notFound);
            Instant now = ops.now();
            if (app.status() != BizApplicationStatus.SCHEDULED || refunds.exists(id)) {
                throw BizApplicationException.stateChanged();
            }
            BizPhase phase = BizPhase.of(app.startAt(), app.endAt(), now);
            ops.event(id, BizEventAction.REFUND_REQUESTED, BizActorType.MEMBER, userId, reason, now);
            switch (phase) {
                case UPCOMING -> {
                    long paid = ops.paidAmount(app);
                    ops.cancelWithFullRefund(app, BizActorType.SYSTEM, null, reason, now);
                    ops.notices().member(app, BizApplicationNotices.Kind.REFUND_APPROVED, paid, null);
                }
                case RUNNING -> {
                    ops.requestProrated(app, BizRefundType.PRORATED, reason, now);
                    ops.notices().member(app, BizApplicationNotices.Kind.REFUND_REQUESTED, 0, null);
                    ops.notices().refundRequested(app);
                }
                case ENDED -> throw BizApplicationException.stateChanged();
            }
        });
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 공개 기간(끝 포함 날짜) — 내일 이후 시작 · 미리 신청 일수 · 최대 일수 */
    private Period publicPeriod(BizFields f, String field, String startText, String endText, PriceRule rule) {
        LocalDate start = f.date(field, startText);
        LocalDate end = f.date(field, endText);
        if (start == null || end == null) {
            return null;
        }
        LocalDate today = today();
        if (end.isBefore(start)) {
            f.add(field, "bizapp.period.invalid");
        } else if (!start.isAfter(today)) {
            f.add(field, "bizapp.period.past");
        } else if (start.isAfter(today.plusDays(rule.maxLeadDays()))) {
            f.add(field, "bizapp.period.lead", rule.maxLeadDays());
        } else if (end.toEpochDay() - start.toEpochDay() + 1 > rule.maxDays()) {
            f.add(field, "bizapp.period.tooLong", rule.maxDays());
        }
        return f.has(field) ? null : BizPeriods.ofDates(start, end);
    }

    /** 노출 없는 핫딜 글 — 내일 0시부터 딜 마감일(포함)까지, 마감일이 없거나 지났으면 7일, 최대 일수까지(가정) */
    private Period defaultDealPeriod(LocalDate endsOn, PriceRule rule) {
        LocalDate start = today().plusDays(1);
        LocalDate end = endsOn != null && !endsOn.isBefore(start) ? endsOn : start.plusDays(DEAL_DEFAULT_DAYS - 1L);
        if (end.toEpochDay() - start.toEpochDay() + 1 > rule.maxDays()) {
            end = start.plusDays(rule.maxDays() - 1L);
        }
        return BizPeriods.ofDates(start, end);
    }

    private static BizProduct placementOf(BizDealInfo deal) {
        if (deal == null || deal.placement() == null) {
            return null;
        }
        return switch (deal.placement().strip()) {
            case "HOME_TOP" -> BizProduct.HOTDEAL_TOP;
            case "CATEGORY_TOP" -> BizProduct.HOTDEAL_CATEGORY;
            default -> null;
        };
    }

    /** 핫딜 정보 검사 → 소재 내용(응답에도 같은 모양) */
    private Map<String, Object> dealContent(BizFields f, String title, BizDealInfo deal) {
        BizDealInfo d = deal == null ? new BizDealInfo(null, null, null, null, null, null, null, null, null) : deal;
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("title", title);
        String link = f.link("deal.linkUrl", d.linkUrl(), false);
        BizDealLinks.check(link).ifPresent(check -> {
            if (check.blocked()) {
                f.add("deal.linkUrl", check.blockedReason());
            }
            c.put("host", check.host());
            c.put("affiliate", check.affiliate());
        });
        c.put("linkUrl", link);
        c.put("shopName", f.text("deal.shopName", d.shopName(), 40));
        if (d.dealPrice() == null || d.dealPrice() < 1) {
            f.add("deal.dealPrice", "bizapp.amount.invalid");
        }
        if (d.listPrice() != null && d.listPrice() < 1) {
            f.add("deal.listPrice", "bizapp.amount.invalid");
        }
        if (d.shippingFee() != null && d.shippingFee() < 0) {
            f.add("deal.shippingFee", "bizapp.amount.invalid");
        }
        c.put("listPrice", d.listPrice());
        c.put("dealPrice", d.dealPrice());
        c.put("shippingFee", d.shippingFee());
        LocalDate endsOn = null;
        if (BizFields.strip(d.endsOn()) != null) {
            endsOn = f.date("deal.endsOn", d.endsOn());
            if (endsOn != null && endsOn.isBefore(today())) {
                f.add("deal.endsOn", "bizapp.period.past");
            }
        }
        c.put("endsOn", endsOn == null ? null : endsOn.toString());
        String stock = f.oneOf("deal.stock", d.stock(), s -> Set.of("PLENTY", "LIMITED", "UNKNOWN").contains(s) ? s : null);
        c.put("stock", stock);
        c.put("body", f.text("deal.body", d.body(), 5000));
        String placement = f.oneOf("deal.placement", d.placement(), s -> Set.of("NONE", "HOME_TOP", "CATEGORY_TOP").contains(s) ? s : null);
        c.put("placement", placement);
        return c;
    }

    private String imageUrl(String storageKey) {
        ImageStorage storage = images.getIfAvailable();
        return storage == null ? null : storage.publicUrl(storageKey);
    }

    private static String orderName(AppRow app) {
        String name = app.title();
        return name.length() <= 100 ? name : name.substring(0, 100);
    }

    private LocalDate today() {
        return ServiceTime.today(ops.clock());
    }
}

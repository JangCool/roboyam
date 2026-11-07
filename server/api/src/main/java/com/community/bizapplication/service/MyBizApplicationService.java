package com.community.bizapplication.service;

import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizCreativeRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.dto.response.BizDealPostView;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizApplicationReader.Bucket;
import com.community.bizapplication.service.BizApplicationReader.Snapshot;
import com.community.bizapplication.service.BizApplicationReader.Summary;
import com.community.bizapplication.service.BizPricing.Amount;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.service.BusinessPii;
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
    private final BizDealContent dealForm;
    private final BizDealPostReader dealPosts;
    private final BizDealEndService dealEnds;
    private final BizPauseService pauses;
    private final BusinessProfileRepository profiles;
    private final BusinessPii pii;

    public MyBizApplicationService(BizApplicationMemberService members, BizApplicationReader reader, BizApplicationProperties props, Messages messages,
                                   BizDealContent dealForm, BizDealPostReader dealPosts, BizDealEndService dealEnds, BizPauseService pauses,
                                   BusinessProfileRepository profiles, BusinessPii pii) {
        this.pauses = pauses;
        this.profiles = profiles;
        this.pii = pii;
        this.members = members;
        this.reader = reader;
        this.props = props;
        this.messages = messages;
        this.dealForm = dealForm;
        this.dealPosts = dealPosts;
        this.dealEnds = dealEnds;
    }

    // ── 상품·남은 칸·링크 ──────────────────────────────────────────────────

    /** {@code accepting} = 요금표 받기, {@code onlinePayment} = 운영 주체가 온라인 결제를 받을 수 있는 단계(개인이면 false — 수동 결제만, 사용자 결정 2026-11-07) */
    public List<BizMemberView.AdProduct> adProducts() {
        boolean selling = members.operatorCanSell();
        return members.adProducts().stream().map(p -> new BizMemberView.AdProduct(p.rule().product().publicName(), p.rule().unitPrice(), p.rule().maxDays(),
                p.rule().maxLeadDays(), p.rule().capacity() == null ? 0 : p.rule().capacity(), p.openDays(), p.rule().accepting(), selling)).toList();
    }

    public BizMemberView.DealProduct dealProduct() {
        Map<BizProduct, PriceRule> all = members.priceRules();
        boolean selling = members.operatorCanSell();
        List<BizMemberView.DealPlacement> placements = List.of(BizProduct.HOTDEAL_TOP, BizProduct.HOTDEAL_CATEGORY).stream().map(all::get)
                .map(r -> new BizMemberView.DealPlacement(r.product().publicName(), r.unitPrice(), r.capacity() == null ? 0 : r.capacity(), r.maxDays(),
                        r.accepting()))
                .toList();
        PriceRule post = all.get(BizProduct.HOTDEAL_POST);
        List<BizMemberView.DealBoard> boards = dealForm.boards().stream().map(b -> new BizMemberView.DealBoard(Long.toString(b.id()), b.slug(), b.name(),
                b.prefixes().stream().map(x -> new BizMemberView.Prefix(Long.toString(x.id()), x.name())).toList())).toList();
        return new BizMemberView.DealProduct(post.unitPrice(), post.accepting(), selling, placements, boards);
    }

    public List<BizMemberView.AvailabilityDay> availability(String slot, String from, String to) {
        return members.availability(slot, from, to).stream().map(d -> new BizMemberView.AvailabilityDay(d.date().toString(), d.remaining(), d.capacity())).toList();
    }

    /** 링크 검사(2차 — 회원 핫딜 링크 규칙 {@code deal_domains} 로 판정, 신청 때 서버가 다시 막는다). 외부 주소를 열지 않는다 */
    public BizMemberView.DealLinkCheck checkLink(String url) {
        if (url == null || url.isBlank() || url.length() > 500 || BizDealLinks.host(url.strip()).isEmpty()) {
            throw ValidationException.of("linkUrl", new MessageRef("bizapp.link.invalid"));
        }
        var v = dealForm.judge(url).orElseThrow(() -> ValidationException.of("linkUrl", new MessageRef("bizapp.link.invalid")));
        String reason = !v.blocked() ? null : messages.get(new MessageRef(v.shortener() ? "bizapp.link.shortener" : "bizapp.link.blocked"));
        return new BizMemberView.DealLinkCheck(v.host(), v.storeName(), v.blocked(), reason, v.affiliate());
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

    /**
     * 온라인 결제 시작 — 결제창 구매자 칸(포트원은 전화번호가 비면 결제창을 열지 않는다)을 사업자 신원에서 채운다: 이름 = 상호, 전화 = 연락처 평문(숫자만), 이메일 = 신원 이메일(없으면 빈 글자).
     * 연락처가 없으면 결제 주문을 만들기 전에 400 {@code BIZ_CONTACT_MISSING}(「사업자 정보에 연락처를 넣어 주세요」 — 가정, biz-pause-contract 「결제창 구매자」).
     */
    public BizMemberView.PaymentStart startPayment(long userId, long id) {
        BizMemberView.Customer customer = customer(userId);
        PaymentCheckout checkout = members.startPayment(userId, id);
        return new BizMemberView.PaymentStart("BIZ_APPLICATION", Long.toString(id), checkout.expiresAt(), PaymentCheckoutView.of(checkout), customer);
    }

    private BizMemberView.Customer customer(long userId) {
        BusinessProfileEntity profile = profiles.findByUserId(userId).filter(p -> p.getErasedAt() == null).orElse(null);
        String phoneText = profile == null ? null : pii.decrypt(BusinessPii.PHONE, profile.getPhoneEnc());
        String phone = phoneText == null ? "" : phoneText.replaceAll("\\D", "");
        if (phone.isEmpty()) {
            throw BizApplicationException.contactMissing();
        }
        String email = pii.decrypt(BusinessPii.EMAIL, profile.getEmailEnc());
        return new BizMemberView.Customer(profile.getCompanyName() == null ? "" : profile.getCompanyName(), phone, email == null ? "" : email);
    }

    /** 사업자 종료(biz-hotdeal-contract 규칙 6) — 글의 딜을 작성자 종료로 끝낸다. 환불 없음 · 신청 건 상태 그대로 · 처리 기록 DEAL_ENDED_BY_BUSINESS. 응답 = 새 「게시된 글」 */
    public BizDealPostView endDeal(long userId, long id) {
        dealEnds.end(userId, id);
        Snapshot s = reader.snapshotOwned(id, userId);
        return dealPosts.view(s.app(), s.items(), s.creative());
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
                str(c.get("imageUrl")) == null ? null : fileName(str(c.get("imageUrl"))), str(c.get("linkUrl")), str(c.get("altText")), str(c.get("text")),
                str(c.get("imageFileId"))) : null;
        BizDealInfo deal = a.kind() == BizApplicationKind.HOTDEAL_POST ? new BizDealInfo(str(c.get("linkUrl")), str(c.get("shopName")), num(c.get("listPrice")),
                num(c.get("dealPrice")), num(c.get("shippingFee")), str(c.get("endsOn")), str(c.get("stock")), str(c.get("body")), str(c.get("placement")),
                str(c.get("boardId")), str(c.get("prefixId")), str(c.get("imageFileId")), str(c.get("imageUrl"))) : null;
        BizMemberView.Deposit deposit = null;
        if (a.paymentMethod() == BizPaymentMethod.MANUAL) {
            BizApplicationProperties.DepositAccount acc = props.depositAccount();
            deposit = new BizMemberView.Deposit(blankToNull(acc.bankName()), blankToNull(acc.accountNo()), blankToNull(acc.accountHolder()), a.businessName(),
                    s.contracts().stream().anyMatch(x -> x.active()) ? "RECEIVED" : "WAITING", s.deposit() != null);
        }
        RefundRow r = s.refund();
        // 추가 환불(CLAIM)의 사유는 클레임 내용이라 관리자만 본다(biz-pause-contract 「추가 환불」 — 공개 응답엔 싣지 않는다)
        boolean claim = r != null && r.type() == BizRefundType.CLAIM;
        BizMemberView.Refund refund = r == null ? null : new BizMemberView.Refund(refundType(r.type()), r.status().name(), claim ? null : r.reason(), r.requestedAt(),
                r.proposedAmount(), r.approvedAmount(), claim ? null : r.decisionReason(), r.proratedAmount(), r.feeRate(), r.feeAmount(), r.basisAt());
        List<BizMemberView.History> history = s.events().stream().filter(e -> publicAction(e.action()))
                .map(e -> new BizMemberView.History(e.at(), actor(e.actorType()), e.action(), publicNote(e.action(), e.note()))).toList();
        BizPauseService.PauseInfo p = pauses.info(a, s.items());
        BizMemberView.Pause pause = p == null ? null : new BizMemberView.Pause(p.used(), p.limit(), p.pausedAt(), p.pausedBy() == null ? null : actor(p.pausedBy()),
                p.channel() == null ? null : p.channel().name(), p.pausedMinutes(), p.stoppedAt(), p.refundFrom(), p.proratableTotal(), p.feeRate());
        return new BizMemberView.Detail(sum.id(), sum.kind(), sum.title(), sum.adType(), sum.placement(), sum.startDate(), sum.endDate(), sum.status(), sum.phase(),
                sum.paymentMethod(), sum.amount(), sum.amountConfirmed(), sum.paymentDueAt(), sum.depositDueAt(), sum.submittedAt(), sum.refundRequested(),
                sum.takedownCause(), amount(a.quote()), a.approved() == null ? null : amount(a.approved()), a.adjustReason(), creative, deal,
                a.rejectCode() == null ? null : a.rejectCode().name(), a.rejectText(), a.takedownText(), deposit, refund, history,
                dealPosts.view(a, s.items(), s.creative()), pause, refundLines(a.id()), refundTotals(a, s));
    }

    /** 환불 내역(오래된 차례) — 종류·상태·금액·시각만(사유·운영자 메모 없음) */
    private List<BizMemberView.RefundLine> refundLines(long id) {
        return reader.refunds(id).stream().map(r -> new BizMemberView.RefundLine(refundType(r.type()), r.status().name(),
                r.approvedAmount() != null ? r.approvedAmount() : r.proposedAmount(), r.requestedAt(), r.decidedAt(),
                r.method() == BizPaymentMethod.MANUAL ? r.transferredAt() : r.completedAt())).toList();
    }

    private BizMemberView.RefundTotals refundTotals(AppRow a, Snapshot s) {
        BizRefundSummary sum = reader.refundSummary(a, s.deposit());
        return sum == null ? null : new BizMemberView.RefundTotals(sum.paid(), sum.refunded());
    }

    /** 공개 환불 종류 — 시작 전 전액 FULL_AUTO · 광고주 요청 중단 STOP_PRORATED · 운영자 추가 환불 CLAIM · 그 밖 일할 심사 PRORATED_REVIEW */
    private static String refundType(BizRefundType type) {
        return switch (type) {
            case FULL_AUTO -> "FULL_AUTO";
            case STOP_PRORATED -> "STOP_PRORATED";
            case CLAIM -> "CLAIM";
            default -> "PRORATED_REVIEW";
        };
    }

    /**
     * 공개 처리 기록의 note — 정지·다시 켜기·중단의 운영자 대신 처리 토큰은 요청 경로까지만(메모는 관리자에게만 — biz-pause-contract 「데이터 모양」).
     */
    static String publicNote(String action, String note) {
        if (note == null || !(BizEventAction.PAUSED.name().equals(action) || BizEventAction.RESUMED.name().equals(action) || BizEventAction.STOPPED.name().equals(action))) {
            return note;
        }
        List<String> kept = new java.util.ArrayList<>();
        for (String token : note.split(" · ", -1)) {
            kept.add(token);
            if (token.equals("PHONE") || token.equals("EMAIL") || token.equals("OTHER")) {
                break; // 경로 뒤는 메모
            }
        }
        return String.join(" · ", kept);
    }

    // ── 일시 정지·중단(V127) ───────────────────────────────────────────────

    public BizMemberView.Detail pause(long userId, long id) {
        pauses.pause(userId, id);
        return detail(userId, id);
    }

    public BizMemberView.Detail resume(long userId, long id) {
        pauses.resume(userId, id);
        return detail(userId, id);
    }

    public BizMemberView.Detail stop(long userId, long id, String reason) {
        pauses.stop(userId, id, reason);
        return detail(userId, id);
    }

    public BizMemberView.RefundPolicy refundPolicy() {
        BizApplicationMemberService.RefundPolicy p = members.refundPolicy();
        return new BizMemberView.RefundPolicy(p.version(), p.stopFeeRate());
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 목록·상세·성과 요약({@link MyBizAdStatsService})이 같이 쓰는 한 줄 */
    static BizMemberView.Summary summary(AppRow a, List<ItemRow> items, CreativeRow creative, RefundRow refund, Instant now) {
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
                a.depositDeadlineAt(), a.createdAt(), refund != null, a.takedownCause() == null ? null : a.takedownCause().name());
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

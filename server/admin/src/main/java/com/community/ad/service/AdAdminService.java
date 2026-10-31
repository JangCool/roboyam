package com.community.ad.service;

import com.community.ad.dto.AdAdminRequest;
import com.community.ad.dto.AdAdminView;
import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import com.community.ad.repository.master.AdLedgerStore;
import com.community.bizapplication.dto.BizAdminView;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.dto.BizUpload;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizSource;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizContractStore.ContractRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizApplicationReader.Snapshot;
import com.community.bizapplication.service.BizApplicationReviewService;
import com.community.bizapplication.service.BizFields;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizPricing;
import com.community.bizapplication.service.BizSlotService;
import com.community.business.service.BusinessFields;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 광고 일정표(admin-web {@code /ads}, API {@code /api/admin/ads}) — 응답 만들기와 입력 꺼내기. 장부 읽기는 {@link AdLedgerStore}, 처리 규칙은 shared {@link AdOperatorService}·
 * {@link AdAdvertiserService}·{@link BizApplicationReviewService}(계약서 사본 미리 올리기). 권한은 컨트롤러가 본다(조회 {@code ad.read} — 핫딜 노출 자리 탭도 같은 권한, 04 §4 Q3).
 * 한도 판정은 신청 건 엔진({@link BizSlotService}) 그대로 — 달력은 안내이고 저장(승인·연장) 때 서버가 다시 판정한다(409 {@code BIZ_SLOT_FULL}).
 */
@Service
public class AdAdminService {

    /** 장부 유형(일정표 탭) — 한도가 있는 상품 7개 */
    static final List<BizProduct> TYPES = List.of(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER, BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER,
            BizProduct.TEXT_LINE, BizProduct.HOTDEAL_TOP, BizProduct.HOTDEAL_CATEGORY);
    static final int MAX_PAGE_SIZE = 100;

    private final AdLedgerStore ledger;
    private final AdAdvertiserStore advertiserStore;
    private final AdAdvertiserService advertisers;
    private final AdOperatorService operations;
    private final AdImageUploader images;
    private final BizApplicationReader reader;
    private final BizApplicationReviewService review;
    private final BizSlotService slots;
    private final BizPriceRuleStore rules;
    private final Messages messages;

    public AdAdminService(AdLedgerStore ledger, AdAdvertiserStore advertiserStore, AdAdvertiserService advertisers, AdOperatorService operations, AdImageUploader images,
                          BizApplicationReader reader, BizApplicationReviewService review, BizSlotService slots, BizPriceRuleStore rules, Messages messages) {
        this.ledger = ledger;
        this.advertiserStore = advertiserStore;
        this.advertisers = advertisers;
        this.operations = operations;
        this.images = images;
        this.reader = reader;
        this.review = review;
        this.slots = slots;
        this.rules = rules;
        this.messages = messages;
    }

    // ── 일정표 ──────────────────────────────────────────────────────────────

    /** 그 달(YYYY-MM) — 날짜별 남은 칸 + 유형 요약 + 그 달에 걸친 장부 건 */
    @Transactional(readOnly = true)
    public AdAdminView.Calendar calendar(String typeText, String monthText) {
        BizFields f = BizFields.start();
        BizProduct type = type(f, typeText);
        YearMonth month = null;
        try {
            month = monthText == null ? null : YearMonth.parse(monthText.strip());
        } catch (DateTimeParseException e) {
            month = null;
        }
        if (month == null) {
            f.add("month", "bizapp.value.invalid");
        }
        f.throwIfAny();
        Instant now = reader.now();
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        List<AdAdminView.DaySlot> days = slots.daily(type, first, last).stream()
                .map(d -> new AdAdminView.DaySlot(d.date().toString(), d.remaining(), d.capacity())).toList();
        int running = 0;
        int scheduled = 0;
        int awaiting = 0;
        for (AppRow a : ledger.live(type, now)) {
            switch (BizApplicationReader.adminStatus(a, now)) {
                case "RUNNING" -> running++;
                case "SCHEDULED" -> scheduled++;
                case "AWAITING_PAYMENT", "AWAITING_DEPOSIT" -> awaiting++;
                default -> {
                }
            }
        }
        AdAdminView.TypeSummary summary = new AdAdminView.TypeSummary(type.name(), capacity(type), running, scheduled, awaiting);
        List<AppRow> month0 = ledger.ledger(type, BizPeriods.startOf(first), BizPeriods.startOf(last.plusDays(1)), null);
        return new AdAdminView.Calendar(summary, days, rows(type, month0, now));
    }

    /** 장부 목록 — 유형(필수)·보이는 상태·검색, 시작이 늦은 것 먼저 */
    @Transactional(readOnly = true)
    public AdAdminView.Page list(String typeText, String statusText, String q, Integer page, Integer size) {
        BizFields f = BizFields.start();
        BizProduct type = type(f, typeText);
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        if (p < 0) {
            f.add("page", "bizapp.value.invalid");
        }
        if (s < 1 || s > MAX_PAGE_SIZE) {
            f.add("size", "bizapp.value.invalid");
        }
        f.throwIfAny();
        String status = BizFields.strip(statusText);
        Instant now = reader.now();
        List<AdAdminView.Row> all = rows(type, ledger.ledger(type, null, null, q), now).stream().filter(r -> status == null || status.equals(r.status())).toList();
        int from = Math.min(all.size(), p * s);
        return new AdAdminView.Page(all.subList(from, Math.min(all.size(), from + s)), all.size());
    }

    /** 장부 상세 — 장부 유형 줄이 없는 건(노출 없는 핫딜 글)은 404 */
    @Transactional(readOnly = true)
    public AdAdminView.Detail detail(AdminActor actor, String idText) {
        long id = parseId(idText);
        Snapshot s = reader.snapshot(id);
        BizProduct type = BizApplicationReader.capacityProduct(s.items());
        if (type == null) {
            throw BizApplicationException.notFound();
        }
        Instant now = reader.now();
        AppRow a = s.app();
        AdAdminView.Row row = row(type, a, advertiserOf(a, a.advertiserId() == null ? null : advertiserStore.find(a.advertiserId()).orElse(null)), now);
        BizAdminView.Amount amount = a.approved() != null ? amount(a.approved())
                : a.proposedSupply() != null ? amount(BizPricing.amountOf(a.proposedSupply())) : amount(a.quote());
        List<CreativeRow> versions = reader.creativeVersions(id);
        String approver = s.approvedByName();
        List<AdAdminView.Creative> creatives = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            CreativeRow c = versions.get(i);
            Map<String, Object> m = c.content();
            creatives.add(new AdAdminView.Creative(c.version(), str(m.get("imageUrl")), str(m.get("text")), str(m.get("linkUrl")), str(m.get("altText")), c.approvedAt(),
                    c.approvedAt() == null ? null : approver, i == versions.size() - 1));
        }
        BizAdminView.OnlinePayment online = a.paidAt() == null ? null : new BizAdminView.OnlinePayment(a.paidAt(), s.payment() == null ? null : s.payment().pgTxId());
        List<BizAdminView.Contract> contracts = s.contracts().stream().map(AdAdminService::contract).toList();
        BizAdminView.Deposit deposit = s.deposit() == null ? null : new BizAdminView.Deposit(s.deposit().amount(), s.deposit().depositedOn(), s.deposit().depositorName(),
                s.deposit().mismatchReason(), s.deposit().confirmedByName(), s.deposit().confirmedAt());
        AdAdminView.Takedown takedown = a.takenDownAt() == null ? null : new AdAdminView.Takedown(a.takenDownAt(), a.takedownCause().name(), a.takedownText(),
                a.takenDownBy() == null ? null : reader.operatorName(a.takenDownBy()), "SENT".equals(a.takedownMail()), a.takedownMail());
        RefundRow r = s.refund();
        BizAdminView.Refund refund = r == null ? null : new BizAdminView.Refund(r.requestedAt(), r.reason(), r.paidAmount(), r.proposedAmount(), r.usedDays(),
                r.totalDays(), r.status().name(), r.type().name(), r.method().name(), r.approvedAmount(), r.decisionReason(), r.transferStatus(), r.transferredAt(),
                r.completedAt());
        BizAdminView.Registrant registrant = a.source() == BizSource.OPERATOR && a.createdBy() != null
                ? new BizAdminView.Registrant(Long.toString(a.createdBy()), reader.operatorName(a.createdBy()), a.createdBy() == actor.operatorId()) : null;
        List<AdAdminView.History> history = s.events().stream().map(e -> new AdAdminView.History(e.at(), e.action(), actorName(e.actorType(), e.actorName()), e.note()))
                .toList();
        return new AdAdminView.Detail(row.id(), row.type(), row.advertiser(), row.period(), row.status(), row.paymentMethod(), row.weight(), row.source(),
                row.applicationNo(), row.lastActionAt(), row.domain(), amount, a.proposedSupply(), creatives, online, contracts, deposit, takedown, refund, a.operatorMemo(),
                registrant, history);
    }

    /** 기간 판정 — 그 기간 가장 빠듯한 칸 · 꽉 찬 날(한국 날짜) · 요금표 계산(자기 자신 없음 — 새 등록) */
    @Transactional(readOnly = true)
    public AdAdminView.Availability availability(String typeText, String startText, String endText) {
        BizFields f = BizFields.start();
        BizProduct type = type(f, typeText);
        Instant start = f.instant("period", startText);
        Instant end = f.has("period") ? null : f.instant("period", endText);
        if (start != null && end != null && (!end.isAfter(start) || !BizPeriods.minuteAligned(start) || !BizPeriods.minuteAligned(end))) {
            f.add("period", "bizapp.period.invalid");
        } else if (start != null && end != null && BizPricing.serviceDays(start, end) > 400) {
            f.add("period", "bizapp.period.range", 400);
        }
        f.throwIfAny();
        BizSlotService.Room room = slots.room(type, start, end, null);
        List<String> full = slots.daily(type, BizPeriods.startDate(start), BizPeriods.endDate(end)).stream().filter(d -> d.remaining() <= 0)
                .map(d -> d.date().toString()).toList();
        PriceRule rule = rules.find(type).orElseThrow();
        int units = BizPricing.days(start, end);
        long supply = rule.unitPrice() * units;
        com.community.bizapplication.service.BizPricing.Amount total = BizPricing.amountOf(supply);
        BizAdminView.Quote quote = new BizAdminView.Quote(rule.unitPrice(), units, "DAY", total.supply(), total.vat(), total.total(),
                List.of(new BizAdminView.QuoteLine(type.name(), "DAY", rule.unitPrice(), units, supply)));
        return new AdAdminView.Availability(room.remaining(), room.capacity(), full, quote);
    }

    // ── 광고주 ──────────────────────────────────────────────────────────────

    public List<AdAdvertiserService.Option> advertisers(String q) {
        return advertisers.search(q);
    }

    public AdAdvertiserService.Option createAdvertiser(AdminActor actor, AdAdminRequest.AdvertiserCreate body) {
        AdAdminRequest.AdvertiserCreate b = body == null ? new AdAdminRequest.AdvertiserCreate(null, null, null, null, null, null) : body;
        return advertisers.create(op(actor), new AdAdvertiserService.Create(b.name(), b.contactName(), b.contactPhone(), b.email(), b.memo(), b.businessId()));
    }

    public AdAdvertiserService.Revealed reveal(AdminActor actor, String id, AdAdminRequest.Reveal body) {
        return advertisers.reveal(op(actor), id, body == null ? null : body.reason());
    }

    // ── 등록 ────────────────────────────────────────────────────────────────

    /** 계약서 사본 미리 올리기(multipart {@code file} 한 장) */
    public BizAdminView.Contract uploadContract(AdminActor actor, MultipartFile file) {
        return contract(review.uploadDraftContract(op(actor), upload(file)));
    }

    /** 광고 이미지 올리기(multipart {@code file}) */
    public AdAdminView.Image uploadImage(AdminActor actor, MultipartFile file) {
        return images.upload(actor.operatorId(), file);
    }

    public AdAdminView.Detail createManual(AdminActor actor, AdAdminRequest.Manual body) {
        AdAdminRequest.Manual b = body == null ? new AdAdminRequest.Manual(null, null, null, null, null, null, null, null) : body;
        AdAdminRequest.Period p = b.period() == null ? new AdAdminRequest.Period(null, null) : b.period();
        AdAdminRequest.Creative c = b.creative() == null ? new AdAdminRequest.Creative(null, null, null, null) : b.creative();
        long id = operations.createManual(op(actor), new AdOperatorService.Manual(b.advertiserId(), b.type(), p.startAt(), p.endAt(), b.weight(),
                new AdOperatorService.Creative(c.imageFileId(), c.text(), c.linkUrl(), c.altText()), b.supply(), b.contractFileIds(), b.memo()));
        return detail(actor, Long.toString(id));
    }

    // ── 긴급 내림·되돌리기 ──────────────────────────────────────────────────

    public AdAdminView.Detail takedown(AdminActor actor, String idText, AdAdminRequest.Takedown body) {
        long id = ledgerId(idText);
        operations.takedown(op(actor), id, body == null ? null : body.reason(), body != null && Boolean.TRUE.equals(body.notifyMail()));
        return detail(actor, Long.toString(id));
    }

    public AdAdminView.Detail restore(AdminActor actor, String idText, AdAdminRequest.Restore body) {
        long id = ledgerId(idText);
        operations.restore(op(actor), id, body == null ? null : body.compensation(), body == null ? null : body.reason());
        return detail(actor, Long.toString(id));
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 장부에 드는 건의 ID — 없거나 장부 유형 줄이 없으면 404 */
    private long ledgerId(String idText) {
        long id = parseId(idText);
        if (BizApplicationReader.capacityProduct(reader.snapshot(id).items()) == null) {
            throw BizApplicationException.notFound();
        }
        return id;
    }

    private List<AdAdminView.Row> rows(BizProduct type, List<AppRow> apps, Instant now) {
        List<Long> advertiserIds = apps.stream().map(AppRow::advertiserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, AdvertiserRow> byId = advertiserIds.isEmpty() ? Collections.emptyMap() : advertiserStore.findAll(advertiserIds);
        return apps.stream().map(a -> row(type, a, advertiserOf(a, a.advertiserId() == null ? null : byId.get(a.advertiserId())), now)).toList();
    }

    private static AdAdminView.Row row(BizProduct type, AppRow a, AdAdvertiserService.Option advertiser, Instant now) {
        return new AdAdminView.Row(Long.toString(a.id()), type.name(), advertiser, new BizAdminView.Period(a.startAt(), a.endAt()), BizApplicationReader.adminStatus(a, now),
                (a.paymentMethod() != null ? a.paymentMethod() : a.preferredMethod()).name(), a.weight(), a.source() == BizSource.OPERATOR ? "OPERATOR" : "APPLICATION",
                a.no(), a.updatedAt(), a.kind().domain().name());
    }

    /** 건의 광고주 — 계정 없는 광고주(연결됐으면 사업자 표시) · 사업자 · 이름만 */
    static AdAdvertiserService.Option advertiserOf(AppRow a, AdvertiserRow advertiser) {
        if (advertiser != null) {
            return AdAdvertiserService.option(advertiser);
        }
        if (a.businessProfileId() != null) {
            return new AdAdvertiserService.Option("biz-" + a.businessProfileId(), "BUSINESS", a.businessName(), Long.toString(a.businessProfileId()),
                    BusinessFields.maskBizNo(a.bizNoLast4()), null);
        }
        return new AdAdvertiserService.Option(null, "ADVERTISER", a.businessName(), null, null, null);
    }

    private int capacity(BizProduct type) {
        Integer c = rules.find(type).map(PriceRule::capacity).orElse(null);
        return c == null ? 1 : c;
    }

    private static BizProduct type(BizFields f, String text) {
        return f.oneOf("type", text, s -> {
            BizProduct p = BizProduct.valueOf(s);
            return TYPES.contains(p) ? p : null;
        });
    }

    private static BizAdminView.Contract contract(ContractRow c) {
        return new BizAdminView.Contract(Long.toString(c.id()), c.fileName(), c.contentType(), c.sizeBytes(), c.uploadedAt(), c.uploadedByName(), c.replacedAt(),
                c.replacedByName());
    }

    private static BizAdminView.Amount amount(com.community.bizapplication.service.BizPricing.Amount a) {
        return new BizAdminView.Amount(a.supply(), a.vat(), a.total());
    }

    private String actorName(BizActorType type, String name) {
        if (type == BizActorType.SYSTEM) {
            return messages.get(new MessageRef("business.history.actor.SYSTEM"));
        }
        return name == null ? messages.get(new MessageRef("business.history.actor.UNKNOWN")) : name;
    }

    private static BizUpload upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef("bizapp.file.required"));
        }
        try {
            return new BizUpload(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("bizapp.file.required"));
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static BizOperator op(AdminActor actor) {
        return new BizOperator(actor.operatorId(), actor.ip());
    }

    /** ID 글자 — 틀리면 404 */
    static long parseId(String text) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw BizApplicationException.notFound();
        }
    }
}

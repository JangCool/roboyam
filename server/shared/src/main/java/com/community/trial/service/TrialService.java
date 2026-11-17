package com.community.trial.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore.TrialTerms;
import com.community.bizapplication.service.BizPricing;
import com.community.commerce.image.GroupBuyImageUploader;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.TrialActorType;
import com.community.trial.entity.TrialApplicationEntity;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageReportEntity;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialDamageVerdict;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialForm;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialPhotoEntity;
import com.community.trial.entity.TrialPhotoKind;
import com.community.trial.entity.TrialQuota;
import com.community.trial.entity.TrialShipper;
import com.community.trial.entity.TrialStatus;
import com.community.trial.entity.TrialStockReceiptEntity;
import com.community.trial.exception.TrialException;
import com.community.trial.repository.master.TrialApplicationRepository;
import com.community.trial.repository.master.TrialDamageReportRepository;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.trial.repository.master.TrialPhotoRepository;
import com.community.trial.repository.master.TrialQueries;
import com.community.trial.repository.master.TrialRepository;
import com.community.trial.repository.master.TrialStockReceiptRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험단 모집 처리(trial-group-contract 「구현 결정」 1·2·10, 「운영자가 대신 보낼 때」·「대여형 파손」) — 관리자 API 와 광고주 화면(사업자 센터)이 같이 쓴다. 처리한 쪽은 {@link Actor}
 * (운영자 · 광고주 회원)이고, 광고주는 자기 모집만·광고주가 보내는 모집의 발송·반납·파손만 한다(그 밖 404 {@code TRIAL_NOT_FOUND} · 409 {@code TRIAL_WRONG_SHIPPER}).
 *
 * <ul>
 *   <li><b>만들기</b>: 광고주·상품·제품값·형태(제공형은 제품값 ≤ 설정 상한 — 넘으면 400 {@code fields.form})·사용기 게시판(「체험단 사용기 받기」 켠 일반 게시판, 없으면 slug review)·인원·
 *       리뷰어 몫·먼저 신청 시간·신청 기간(분 단위)·기한·보내는 쪽. 광고 신청 건(kind TRIAL, 운영자 등록, 0원) + 상품 줄(TRIAL) 을 같은 트랜잭션에 만든다. 요금은 그때의 요금표 값 사본.</li>
 *   <li><b>뽑기</b>: 신청 마감 뒤(모집 중 + 마감 지남 · 뽑는 중). 리뷰어 몫 신청자는 리뷰어 몫을 먼저 채우고 넘치면 일반 몫, 일반 신청자는 일반 몫만 — 넘으면 400 {@code fields.applicationIds}.
 *       나머지 신청은 안 뽑힘(주소 사본 바로 파기). 금액 확정(인원당이면 인원 × 단가, 대신 발송이면 + 인원 × 발송비) → 광고 신청 건 견적에 굳힌다. 알림 뽑힘·안 뽑힘.</li>
 *   <li><b>입고</b>(운영자가 보낼 때만): 수량 + 상태 사진(없으면 400 {@code fields.photoIds}). <b>발송</b>: 운영자는 입고 수량까지, 광고주는 발송 전 사진 필수. 송장이 들어가면 알림.</li>
 *   <li><b>반납 받음</b>(대여형): 운영자 또는 광고주(광고주가 보내는 모집) — 반납 사진 필수. 기한 안 사용기가 연결돼 있으면 약속 지킴. <b>광고주 반송</b>(운영자): 송장.</li>
 *   <li><b>파손 신고</b>(대여형, 사진 필수) → 회원 알림(7일 안 이의) → <b>판정</b>(이의했거나 이의 기한이 지난 뒤) — 불이익은 {@link TrialPromiseService#damageVerdict}.</li>
 * </ul>
 * 감사 기록: 만들기·고치기·뽑기·입고·발송·반납·반송·파손 신고·판정.
 */
@Service
public class TrialService {

    /** 파손 이의 제기 기한(일 — 계약 고정) */
    public static final int OBJECTION_DAYS = 7;
    /** 사진 한 처리에 붙이는 최대 수 */
    public static final int PHOTOS_MAX = 10;
    public static final String DEFAULT_REVIEW_BOARD = "review";

    /** 처리한 쪽 — 운영자(관리자 앱) · 광고주(사업자 센터 회원, 광고주 ID 까지) */
    public record Actor(TrialActorType type, long id, String ip, Long advertiserId) {

        public static Actor operator(long operatorId, String ip) {
            return new Actor(TrialActorType.OPERATOR, operatorId, ip, null);
        }

        public static Actor advertiser(long userId, long advertiserId, String ip) {
            return new Actor(TrialActorType.ADVERTISER, userId, ip, advertiserId);
        }

        boolean operator() {
            return type == TrialActorType.OPERATOR;
        }

        AuditEvent event(String action) {
            return operator() ? AuditEvent.byOperator(id, ip, action, AuditResult.SUCCESS) : AuditEvent.byMember(id, ip, action, AuditResult.SUCCESS);
        }
    }

    private final TrialRepository trials;
    private final TrialApplicationRepository applications;
    private final TrialParticipantRepository participants;
    private final TrialStockReceiptRepository stocks;
    private final TrialPhotoRepository photos;
    private final TrialDamageReportRepository damages;
    private final TrialQueries queries;
    private final TrialSettingsService settings;
    private final TrialPromiseService promise;
    private final TrialViewer viewer;
    private final BizApplicationStore bizApplications;
    private final BizPriceRuleStore prices;
    private final AdAdvertiserStore advertisers;
    private final BoardCatalog catalog;
    private final GroupBuyImageUploader images;
    private final MemberNotificationPublisher notices;
    private final AuditLogService audit;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;
    private final TrialPayments payments;

    public TrialService(TrialRepository trials, TrialApplicationRepository applications, TrialParticipantRepository participants, TrialStockReceiptRepository stocks,
                        TrialPhotoRepository photos, TrialDamageReportRepository damages, TrialQueries queries, TrialSettingsService settings,
                        TrialPromiseService promise, TrialViewer viewer, BizApplicationStore bizApplications, BizPriceRuleStore prices, AdAdvertiserStore advertisers,
                        BoardCatalog catalog, GroupBuyImageUploader images, MemberNotificationPublisher notices, AuditLogService audit, Messages messages,
                        SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks, TrialPayments payments) {
        this.trials = trials;
        this.applications = applications;
        this.participants = participants;
        this.stocks = stocks;
        this.photos = photos;
        this.damages = damages;
        this.queries = queries;
        this.settings = settings;
        this.promise = promise;
        this.viewer = viewer;
        this.bizApplications = bizApplications;
        this.prices = prices;
        this.advertisers = advertisers;
        this.catalog = catalog;
        this.images = images;
        this.notices = notices;
        this.audit = audit;
        this.messages = messages;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
        this.payments = payments;
    }

    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TrialViews.TrialRow> list(String statusRaw) {
        TrialStatus status = statusRaw == null || statusRaw.isBlank() ? null : TrialStatus.parse(statusRaw)
                .orElseThrow(() -> ValidationException.of("status", new MessageRef("trial.value.invalid")));
        List<TrialEntity> rows = queries.list(status);
        Map<Long, Long> counts = queries.applicantCounts(rows.stream().map(TrialEntity::getId).toList());
        return viewer.rows(rows, counts, now());
    }

    /** 광고주의 모집 목록(초안 빼고) */
    @Transactional(readOnly = true)
    public List<TrialViews.TrialRow> listOf(long advertiserId) {
        List<TrialEntity> rows = queries.byAdvertiser(advertiserId);
        Map<Long, Long> counts = queries.applicantCounts(rows.stream().map(TrialEntity::getId).toList());
        return viewer.rows(rows, counts, now());
    }

    @Transactional(readOnly = true)
    public TrialViews.TrialDetail detail(String idText, Actor actor) {
        TrialEntity t = visible(idText, actor);
        return viewer.detail(t, actor.type(), now());
    }

    @Transactional(readOnly = true)
    public TrialViews.ApplicantPage applicants(String idText, Actor actor) {
        return viewer.applicants(visible(idText, actor), actor.type());
    }

    /** 보는 쪽에게 보이는 모집 — 광고주는 자기 모집(초안 빼고)만, 아니면 404 */
    private TrialEntity visible(String idText, Actor actor) {
        TrialEntity t = trials.findById(id(idText, TrialException::notFound)).orElseThrow(TrialException::notFound);
        requireOwner(t, actor);
        return t;
    }

    private static void requireOwner(TrialEntity t, Actor actor) {
        if (!actor.operator() && (!t.getAdvertiserId().equals(actor.advertiserId()) || t.getStatus() == TrialStatus.DRAFT)) {
            throw TrialException.notFound();
        }
    }

    // ── 만들기·고치기·상태 ───────────────────────────────────────────────────

    @Transactional
    public TrialViews.TrialDetail create(long operatorId, String ip, TrialViews.TrialForm body) {
        TrialEntity.Fields f = fields(body, null);
        TrialTerms terms = prices.trialTerms(now()).orElseThrow(() -> new IllegalStateException("TRIAL 요금표 줄이 없다"));
        AdAdvertiserStore.AdvertiserRow adv = advertisers.find(f.advertiserId()).orElseThrow();
        Instant now = now();
        long appId = ids.nextId();
        bizApplications.insertTrial(appId, adv.businessProfileId(), adv.id(), adv.name(), adv.companyName(), adv.bizNoLast4(), f.title(), f.applyStartAt(),
                f.applyEndAt(), operatorId, now);
        bizApplications.insertItem(new BizApplicationStore.ItemRow(ids.nextId(), appId, BizProduct.TRIAL, terms.unitPrice(), 1, terms.unitPrice(), f.applyStartAt(),
                f.applyEndAt()), now);
        TrialEntity t = TrialEntity.create(ids.nextId(), appId, f, terms.priceUnit(), terms.unitPrice(), terms.shippingFeePerPerson(), operatorId, now);
        trials.persist(t);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.TRIAL_CREATE, AuditResult.SUCCESS).target("TRIAL", Long.toString(t.getId()))
                .with("applicationId", Long.toString(appId)).with("after", snapshot(t)));
        return viewer.detail(t, TrialActorType.OPERATOR, now);
    }

    /**
     * 고치기 — 본문 {@code action} 이 있으면 상태만 바꾼다(PUBLISH · CLOSE_APPLY · CLOSE · CANCEL), 없으면 칸을 고친다(뽑기 전 — 준비·모집 중·뽑는 중만, 신청이 있으면 형태·보내는 쪽은
     * 바꿀 수 없다 409 {@code TRIAL_STATE_INVALID}).
     */
    @Transactional
    public TrialViews.TrialDetail update(long operatorId, String ip, String idText, TrialViews.TrialForm body) {
        long trialId = id(idText, TrialException::notFound);
        String action = body == null || body.action() == null ? null : body.action().strip().toUpperCase(java.util.Locale.ROOT);
        // 끝내기·취소는 광고 신청 건(결제)도 함께 움직인다 — 열린 결제 → 신청 건 → 모집 차례로 잠근다(광고 쪽 처리와 같은 차례)
        TrialPayments.Locked locked = "CANCEL".equals(action) || "CLOSE".equals(action) ? payments.lockFor(trialId, "TRIAL_" + action) : null;
        TrialEntity t = locked != null ? locked.trial() : trials.findWithLockById(trialId).orElseThrow(TrialException::notFound);
        Instant now = now();
        Map<String, Object> before = snapshot(t);
        if (action != null && !action.isEmpty()) {
            transition(t, action, operatorId, now, locked == null ? null : locked.app());
        } else {
            if (t.getStatus() != TrialStatus.DRAFT && t.getStatus() != TrialStatus.RECRUITING && t.getStatus() != TrialStatus.SELECTING) {
                throw TrialException.stateInvalid();
            }
            TrialEntity.Fields f = fields(body, t);
            boolean anyApplication = applications.countByTrialIdAndStatusNot(t.getId(), TrialApplicationStatus.CANCELLED) > 0;
            if (anyApplication && (f.form() != t.getForm() || f.shipper() != t.getShipper())) {
                throw TrialException.stateInvalid(); // 신청 동의 항목이 형태·보내는 쪽으로 정해진다
            }
            t.edit(f, now);
            bizApplications.retitleTrial(t.getBizApplicationId(), f.title(), f.applyStartAt(), f.applyEndAt(), now);
        }
        Map<String, Object> after = snapshot(t);
        Map<String, Object> b = new LinkedHashMap<>();
        Map<String, Object> a = new LinkedHashMap<>();
        for (String k : after.keySet()) {
            if (!java.util.Objects.equals(before.get(k), after.get(k))) {
                b.put(k, before.get(k));
                a.put(k, after.get(k));
            }
        }
        if (!a.isEmpty()) {
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.TRIAL_UPDATE, AuditResult.SUCCESS).target("TRIAL", Long.toString(t.getId())).with("before", b)
                    .with("after", a));
        }
        return viewer.detail(t, TrialActorType.OPERATOR, now);
    }

    private void transition(TrialEntity t, String action, long operatorId, Instant now, BizApplicationStore.AppRow app) {
        TrialStatus s = t.getStatus();
        switch (action) {
            case "PUBLISH" -> {
                if (s != TrialStatus.DRAFT) {
                    throw TrialException.stateInvalid();
                }
                t.changeStatus(TrialStatus.RECRUITING, now);
            }
            case "CLOSE_APPLY" -> {
                if (s != TrialStatus.RECRUITING) {
                    throw TrialException.stateInvalid();
                }
                t.changeStatus(TrialStatus.SELECTING, now);
            }
            case "CLOSE" -> {
                if (s != TrialStatus.RUNNING) {
                    throw TrialException.stateInvalid();
                }
                payments.close(t, app, operatorId, now); // L5 — 결제 전 건 정리 · 결제 끝 건 닫은 시각 · 보내지 않은 줄 주소 파기
            }
            // 취소 — 뽑기 전이면 그대로, 진행 중이면 발송 전에만(결제 전 = 신청 건 취소 · 결제 뒤 = 전액 환불). 발송이 있었으면 409 TRIAL_SHIPPING_STARTED
            case "CANCEL" -> payments.cancel(t, app, "TRIAL_CANCELLED", operatorId, false, now);
            default -> throw ValidationException.of("action", new MessageRef("trial.value.invalid"));
        }
    }

    /** 칸 검사 — 모두 모아 400(칸 경로 키) */
    private TrialEntity.Fields fields(TrialViews.TrialForm b, TrialEntity current) {
        if (b == null) {
            throw ValidationException.of("title", new MessageRef("trial.field.required"));
        }
        Map<String, MessageRef> f = new LinkedHashMap<>();
        Long advertiserId = parseId(b.advertiserId());
        if (advertiserId == null) {
            f.put("advertiserId", new MessageRef("trial.field.required"));
        } else {
            var adv = advertisers.find(advertiserId).orElse(null);
            if (adv == null || adv.archivedAt() != null) {
                f.put("advertiserId", new MessageRef("trial.advertiser.invalid"));
            }
        }
        String title = text(b.title(), 100, "title", f);
        String product = text(b.productName(), 100, "productName", f);
        long price = b.productPrice() == null ? -1 : b.productPrice();
        if (price < 0 || price > 1_000_000_000L) {
            f.put("productPrice", new MessageRef("trial.range", "0", "1000000000"));
        }
        TrialForm form = TrialForm.parse(b.form()).orElse(null);
        if (form == null) {
            f.put("form", new MessageRef("trial.value.invalid"));
        } else if (form == TrialForm.GIVE && price >= 0 && price > settings.current().givePriceLimit()) {
            f.put("form", new MessageRef("trial.form.giveLimit", String.valueOf(settings.current().givePriceLimit()))); // 제공형 상한 — 대여형으로만
        }
        Long boardId = parseId(b.reviewBoardId());
        EffectiveBoard board = boardId != null ? catalog.snapshot().board(boardId).orElse(null) : catalog.snapshot().board(DEFAULT_REVIEW_BOARD).orElse(null);
        if (board == null || !board.trialReviewsEnabled() || !BoardFields.KIND_GENERAL.equals(board.kind())) {
            f.put("reviewBoardId", new MessageRef("trial.board.invalid"));
        }
        int recruit = range(b.recruitCount(), null, 1, 1000, "recruitCount", f);
        int share = range(b.reviewerShare(), 60, 0, 100, "reviewerShare", f);
        int gold = range(b.reviewerGoldHours(), 48, 0, 168, "reviewerGoldHours", f);
        int silver = range(b.reviewerSilverHours(), 24, 0, 168, "reviewerSilverHours", f);
        if (!f.containsKey("reviewerGoldHours") && !f.containsKey("reviewerSilverHours") && gold < silver) {
            f.put("reviewerGoldHours", new MessageRef("trial.hours.order"));
        }
        Instant start = instant(b.applyStartAt(), "applyStartAt", f);
        Instant end = instant(b.applyEndAt(), "applyEndAt", f);
        if (start != null && end != null && !end.isAfter(start)) {
            f.put("applyEndAt", new MessageRef("trial.period.order"));
        }
        int reviewDays = range(b.reviewDays(), settings.current().reviewDays(), 1, 90, "reviewDays", f);
        Integer returnDays = null;
        if (form == TrialForm.LEND) {
            returnDays = range(b.returnDays(), null, 1, 90, "returnDays", f);
        }
        TrialShipper shipper = TrialShipper.parse(b.shipper()).orElse(null);
        if (shipper == null) {
            f.put("shipper", new MessageRef("trial.value.invalid"));
        }
        String description = b.description() == null ? "" : b.description().strip();
        if (description.length() > 5000) {
            f.put("description", new MessageRef("trial.text.length", "5000"));
        }
        String image = b.imageKey() == null || b.imageKey().isBlank() ? null : b.imageKey().strip();
        if (image != null && !(GroupBuyImageUploader.ownKey(image) && image.startsWith("groupbuy/trialcover/"))) {
            f.put("imageKey", new MessageRef("trial.value.invalid"));
        }
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
        return new TrialEntity.Fields(advertiserId, title, product, price, form, board.id(), recruit, share, gold, silver, start, end, reviewDays, returnDays, shipper,
                description, image);
    }

    // ── 뽑기 ─────────────────────────────────────────────────────────────

    @Transactional
    public TrialViews.TrialDetail select(long operatorId, String ip, String idText, TrialViews.SelectRequest body) {
        TrialEntity t = trials.findWithLockById(id(idText, TrialException::notFound)).orElseThrow(TrialException::notFound);
        Instant now = now();
        boolean afterClose = t.getStatus() == TrialStatus.SELECTING || (t.getStatus() == TrialStatus.RECRUITING && !now.isBefore(t.getApplyEndAt()));
        if (!afterClose) {
            throw TrialException.stateInvalid();
        }
        List<String> raw = body == null || body.applicationIds() == null ? List.of() : body.applicationIds();
        Set<Long> picked = new LinkedHashSet<>();
        for (String s : raw) {
            Long id = parseId(s);
            if (id == null) {
                throw ValidationException.of("applicationIds", new MessageRef("trial.select.unknown"));
            }
            picked.add(id);
        }
        if (picked.isEmpty()) {
            throw ValidationException.of("applicationIds", new MessageRef("trial.select.empty"));
        }
        List<TrialApplicationEntity> all = applications.findByTrialIdOrderByCreatedAtAscIdAsc(t.getId());
        Map<Long, TrialApplicationEntity> byId = new HashMap<>();
        all.forEach(a -> byId.put(a.getId(), a));
        for (Long id : picked) {
            TrialApplicationEntity a = byId.get(id);
            if (a == null || a.getStatus() != TrialApplicationStatus.APPLIED) {
                throw ValidationException.of("applicationIds", new MessageRef("trial.select.unknown"));
            }
        }
        int reviewerSlots = t.reviewerSlots();
        int generalSlots = t.getRecruitCount() - reviewerSlots;
        int reviewerUsed = 0;
        int generalUsed = 0;
        Map<Long, TrialQuota> slot = new LinkedHashMap<>();
        // 리뷰어 몫 신청자부터 리뷰어 몫을 채우고, 넘치면 일반 몫 — 일반 신청자는 일반 몫만(계약 「뽑기」 리뷰어 몫·일반 몫 따로)
        for (Long id : picked) {
            if (byId.get(id).getQuota() == TrialQuota.REVIEWER && reviewerUsed < reviewerSlots) {
                slot.put(id, TrialQuota.REVIEWER);
                reviewerUsed++;
            }
        }
        for (Long id : picked) {
            if (slot.containsKey(id)) {
                continue;
            }
            slot.put(id, TrialQuota.GENERAL);
            generalUsed++;
        }
        if (generalUsed > generalSlots || slot.size() > t.getRecruitCount()) {
            throw ValidationException.of("applicationIds", new MessageRef("trial.select.over", String.valueOf(reviewerSlots), String.valueOf(generalSlots)));
        }
        for (TrialApplicationEntity a : all) {
            if (a.getStatus() != TrialApplicationStatus.APPLIED) {
                continue;
            }
            if (slot.containsKey(a.getId())) {
                a.select(now);
                participants.persist(TrialParticipantEntity.create(ids.nextId(), t.getId(), a.getId(), a.getUserId(), slot.get(a.getId()), now));
                notices.trial(a.getUserId(), MemberNotificationType.TRIAL_SELECTED, t.getTitle(), null, "/me/trials");
            } else {
                a.notSelect(now);
                notices.trial(a.getUserId(), MemberNotificationType.TRIAL_NOT_SELECTED, t.getTitle(), null, "/me/trials");
            }
        }
        long amount = TrialPayments.amountOf(t, slot.size());
        t.select(slot.size(), amount, operatorId, now);
        // 견적을 신청 건에 굳힌다(상품 줄 + 대신 발송비 줄) — 금액 > 0 이면 광고 심사 큐 「승인 대기」(승인 → 결제가 끝나야 입고·발송), 0원이면 결제 흐름 밖
        BizPricing.Amount quote = payments.quote(t, slot.size(), amount, operatorId, false, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.TRIAL_SELECT, AuditResult.SUCCESS).target("TRIAL", Long.toString(t.getId()))
                .with("selected", slot.keySet().stream().map(String::valueOf).toList()).with("reviewer", reviewerUsed).with("general", generalUsed)
                .with("priceUnit", t.getPriceUnit()).with("unitPrice", t.getUnitPrice()).with("shippingFeePerPerson", t.getShippingFeePerPerson())
                .with("amount", amount).with("total", quote.total()));
        return viewer.detail(t, TrialActorType.OPERATOR, now);
    }

    /** 후보 추천 켜고 끄기(광고주·운영자) — 신청 대기만 */
    @Transactional
    public void recommend(Actor actor, String applicationIdText, Boolean on) {
        TrialApplicationEntity a = applications.findWithLockById(id(applicationIdText, TrialException::applicationNotFound))
                .orElseThrow(TrialException::applicationNotFound);
        TrialEntity t = trials.findById(a.getTrialId()).orElseThrow(TrialException::notFound);
        try {
            requireOwner(t, actor);
        } catch (TrialException e) {
            throw TrialException.applicationNotFound();
        }
        if (a.getStatus() != TrialApplicationStatus.APPLIED) {
            throw TrialException.stateInvalid();
        }
        a.recommend(on == null || on, now());
    }

    // ── 사진 ─────────────────────────────────────────────────────────────

    /** 사진 올리기(처리에 붙기 전) — 광고주는 발송 전·반납·파손만 */
    @Transactional
    public TrialViews.Uploaded uploadPhoto(Actor actor, String trialIdText, String kindRaw, byte[] data) {
        TrialEntity t = visible(trialIdText, actor);
        TrialPhotoKind kind = TrialPhotoKind.parse(kindRaw).orElseThrow(() -> ValidationException.of("kind", new MessageRef("trial.value.invalid")));
        if (!actor.operator() && kind == TrialPhotoKind.STOCK) {
            throw ValidationException.of("kind", new MessageRef("trial.value.invalid"));
        }
        GroupBuyImageUploader.Uploaded up = images.upload("trial", data);
        TrialPhotoEntity p = TrialPhotoEntity.upload(ids.nextId(), t.getId(), kind, up.key(), actor.type(), actor.id(), now());
        photos.persist(p);
        return new TrialViews.Uploaded(Long.toString(p.getId()), kind.name(), up.key(), up.url());
    }

    /** 모집 대표 그림 올리기(운영자) — 키를 모집 만들기·고치기의 {@code imageKey} 로 */
    public TrialViews.Uploaded uploadCover(byte[] data) {
        GroupBuyImageUploader.Uploaded up = images.upload("trialcover", data);
        return new TrialViews.Uploaded(null, "COVER", up.key(), up.url());
    }

    /** 올린 사진 중 이 모집·종류·올린 쪽의 붙지 않은 것 — 없거나 맞지 않으면 400 {@code fields.photoIds}. {@code required} 면 하나 이상 */
    private List<TrialPhotoEntity> loosePhotos(TrialEntity t, List<String> raw, TrialPhotoKind kind, Actor actor, boolean required) {
        List<Long> idList = new ArrayList<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            Long id = parseId(s);
            if (id == null) {
                throw ValidationException.of("photoIds", new MessageRef("trial.photo.invalid"));
            }
            idList.add(id);
        }
        if (idList.size() > PHOTOS_MAX) {
            throw ValidationException.of("photoIds", new MessageRef("trial.photo.max", String.valueOf(PHOTOS_MAX)));
        }
        if (required && idList.isEmpty()) {
            throw ValidationException.of("photoIds", new MessageRef("trial.photo.required"));
        }
        List<TrialPhotoEntity> found = photos.findByIdIn(idList);
        if (found.size() != new LinkedHashSet<>(idList).size() || found.stream().anyMatch(p -> !p.getTrialId().equals(t.getId()) || p.getKind() != kind
                || p.getAttachedAt() != null || p.getUploadedByType() != actor.type() || (!actor.operator() && p.getUploadedBy() != actor.id()))) {
            throw ValidationException.of("photoIds", new MessageRef("trial.photo.invalid"));
        }
        return found;
    }

    // ── 입고·발송·반납·반송 ─────────────────────────────────────────────────

    /** 운영자 입고 — 운영자가 보내는 모집, 뽑기 뒤(진행 중), 상태 사진 필수 */
    @Transactional
    public TrialViews.TrialDetail stock(long operatorId, String ip, String idText, TrialViews.StockRequest body) {
        TrialEntity t = trials.findWithLockById(id(idText, TrialException::notFound)).orElseThrow(TrialException::notFound);
        Actor actor = Actor.operator(operatorId, ip);
        if (!t.operatorShips()) {
            throw TrialException.wrongShipper();
        }
        if (t.getStatus() != TrialStatus.RUNNING) {
            throw TrialException.stateInvalid();
        }
        payments.requirePaid(t); // 유료면 광고 결제가 끝나야 입고(409 TRIAL_PAYMENT_PENDING)
        int quantity = body == null || body.quantity() == null ? 0 : body.quantity();
        if (quantity < 1 || quantity > 10_000) {
            throw ValidationException.of("quantity", new MessageRef("trial.range", "1", "10000"));
        }
        String note = body.note() == null || body.note().isBlank() ? null : body.note().strip();
        if (note != null && note.length() > 300) {
            throw ValidationException.of("note", new MessageRef("trial.text.length", "300"));
        }
        List<TrialPhotoEntity> attach = loosePhotos(t, body.photoIds(), TrialPhotoKind.STOCK, actor, true); // 사진 없이는 「입고 완료」를 누를 수 없다
        Instant now = now();
        TrialStockReceiptEntity receipt = stocks.findByTrialId(t.getId()).orElse(null);
        if (receipt == null) {
            stocks.persist(TrialStockReceiptEntity.receive(ids.nextId(), t.getId(), quantity, note, operatorId, now));
        } else {
            receipt.change(quantity, note, operatorId, now);
        }
        attach.forEach(p -> p.attach(null, null, now));
        audit.record(actor.event(AuditActions.TRIAL_STOCK_RECEIVE).target("TRIAL", Long.toString(t.getId())).with("quantity", quantity).with("photos", attach.size())
                .with("selected", t.getSelectedCount() == null ? 0 : t.getSelectedCount()));
        return viewer.detail(t, TrialActorType.OPERATOR, now);
    }

    /**
     * 송장 넣기 — 운영자(운영자가 보내는 모집, 입고 수량까지) · 광고주(광고주가 보내는 모집, 발송 전 사진 필수). 받기 전까지 고칠 수 있다. 처음 넣을 때 회원 알림 {@code TRIAL_SHIPPED}.
     */
    @Transactional
    public void ship(Actor actor, String participantIdText, TrialViews.ShipmentRequest body) {
        long participantId = id(participantIdText, TrialException::participantNotFound);
        // 모집 행 → 뽑힌 줄 차례로 잠근다(모집 취소·뽑힌 줄 취소와 같은 차례 — 서로 기다리다 막히지 않게)
        long trialId = queries.trialIdOfParticipant(participantId).orElseThrow(TrialException::participantNotFound);
        TrialEntity t = trials.findWithLockById(trialId).orElseThrow(TrialException::notFound);
        TrialParticipantEntity p = participants.findWithLockById(participantId).orElseThrow(TrialException::participantNotFound);
        ownerOrNotFound(t, actor);
        if (actor.operator() != t.operatorShips()) {
            throw TrialException.wrongShipper();
        }
        if (t.getStatus() != TrialStatus.RUNNING) {
            throw TrialException.stateInvalid();
        }
        if (p.cancelled()) {
            throw TrialException.participantCancelled();
        }
        payments.requirePaid(t); // 유료면 광고 결제가 끝나야 발송(운영자·광고주 모두 — 409 TRIAL_PAYMENT_PENDING)
        if (p.received()) {
            throw TrialException.alreadyReceived();
        }
        String carrier = text(body == null ? null : body.carrier(), 30, "carrier", null);
        String tracking = text(body == null ? null : body.trackingNo(), 40, "trackingNo", null);
        boolean first = !p.shipped();
        if (actor.operator() && first) {
            TrialStockReceiptEntity receipt = stocks.findByTrialId(t.getId()).orElseThrow(TrialException::noStock);
            long shipped = participants.findByTrialIdOrderByIdAsc(t.getId()).stream().filter(TrialParticipantEntity::shipped).count();
            if (shipped >= receipt.getQuantity()) {
                throw TrialException.noStock(); // 입고 수량이 모자란다 — 광고주에게 알리고 기다린다
            }
        }
        // 광고주가 보내면 발송 전 사진 필수(없으면 발송 처리 안 됨) — 이미 붙은 발송 전 사진이 있으면 고칠 때는 새 사진이 없어도 된다
        boolean hasPreShip = photos.findByTrialIdAndAttachedAtIsNotNullOrderByIdAsc(t.getId()).stream()
                .anyMatch(x -> x.getKind() == TrialPhotoKind.PRE_SHIP && p.getId().equals(x.getParticipantId()));
        List<TrialPhotoEntity> attach = loosePhotos(t, body == null ? null : body.photoIds(), TrialPhotoKind.PRE_SHIP, actor, !actor.operator() && !hasPreShip);
        Instant now = now();
        p.ship(carrier, tracking, actor.type(), actor.id(), now);
        attach.forEach(x -> x.attach(p.getId(), null, now));
        if (first) {
            notices.trial(p.getUserId(), MemberNotificationType.TRIAL_SHIPPED, t.getTitle(), carrier + " " + tracking, "/me/trials");
        }
        audit.record(actor.event(AuditActions.TRIAL_SHIP).target("TRIAL", Long.toString(t.getId())).with("participantId", Long.toString(p.getId()))
                .with("carrier", carrier).with("photos", attach.size()).with("first", first));
    }

    /**
     * 뽑힌 줄 운영자 취소(점검 결정 D9 — {@code POST /api/admin/trials/participants/{id}/cancel} {@code { reason }}) — 진행 중 모집에서 아직 아무것도 시작되지 않은 줄만(송장·받음·사용기 연결이
     * 있으면 409 {@code TRIAL_SHIPPING_STARTED}, 이미 취소했으면 409 {@code TRIAL_PARTICIPANT_CANCELLED}, 모집이 진행 중이 아니면 409 {@code TRIAL_STATE_INVALID}). 사유 1~200자(400 {@code fields.reason}).
     * <ul>
     *   <li>주소 사본 바로 파기 · 회원 알림 {@code TRIAL_CANCELLED_BY_OPERATOR}(사유 한 줄).</li>
     *   <li>뽑힌 수는 늘 다시 센다. 금액은 <b>결제 전</b>(신청 건 심사 대기 · 입금 기록 없는 결제·입금 대기)일 때만 다시 센다 — 승인 뒤 결제 전이면 열린 결제를 닫고 심사 대기로 되돌려 다시 승인받는다.
     *       결제 뒤(예약 · 입금 기록 있음)면 금액 그대로(돌려줄 몫은 운영자가 광고 심사의 추가 환불로).</li>
     *   <li>감사 {@code TRIAL_PARTICIPANT_CANCEL}(돈 — 5년).</li>
     * </ul>
     */
    @Transactional
    public TrialViews.TrialDetail cancelParticipant(long operatorId, String ip, String participantIdText, TrialViews.ReasonRequest body) {
        long participantId = id(participantIdText, TrialException::participantNotFound);
        String reason = body == null || body.reason() == null ? "" : body.reason().strip();
        if (reason.isEmpty() || reason.length() > 200) {
            throw ValidationException.of("reason", new MessageRef("trial.cancel.reason"));
        }
        long trialId = queries.trialIdOfParticipant(participantId).orElseThrow(TrialException::participantNotFound);
        TrialPayments.Locked locked = payments.lockFor(trialId, "TRIAL_PARTICIPANT_CANCEL");
        TrialEntity t = locked.trial();
        BizApplicationStore.AppRow app = locked.app();
        TrialParticipantEntity p = participants.findWithLockById(participantId).orElseThrow(TrialException::participantNotFound);
        if (t.getStatus() != TrialStatus.RUNNING) {
            throw TrialException.stateInvalid();
        }
        if (p.cancelled()) {
            throw TrialException.participantCancelled();
        }
        if (p.started()) {
            throw TrialException.shippingStarted();
        }
        Instant now = now();
        p.cancel(operatorId, reason, now);
        applications.findWithLockById(p.getApplicationId()).ifPresent(a -> a.purgeAddress(now));
        notices.trial(p.getUserId(), MemberNotificationType.TRIAL_CANCELLED_BY_OPERATOR, t.getTitle(),
                messages.get(new MessageRef("trial.notice.participantCancelled", reason)), "/me/trials");
        int count = (int) participants.findByTrialIdOrderByIdAsc(t.getId()).stream().filter(x -> !x.cancelled()).count();
        boolean beforePayment = app.status() == com.community.bizapplication.entity.BizApplicationStatus.SUBMITTED
                || (com.community.bizapplication.entity.BizApplicationStatus.AWAITING.contains(app.status()) && bizApplications.deposit(app.id()).isEmpty());
        Long newAmount = null;
        if (beforePayment) {
            newAmount = TrialPayments.amountOf(t, count);
            if (app.status() != com.community.bizapplication.entity.BizApplicationStatus.SUBMITTED
                    && !bizApplications.backToReview(app.id(), app.status(), app.title(), now)) { // 승인 스냅샷·결제 기한을 비운다(열린 결제는 잠글 때 닫았다)
                throw TrialException.stateInvalid();
            }
            payments.quote(t, count, newAmount, operatorId, true, now);
        }
        t.recount(count, newAmount, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.TRIAL_PARTICIPANT_CANCEL, AuditResult.SUCCESS).target("TRIAL", Long.toString(t.getId()))
                .with("participantId", Long.toString(p.getId())).with("reason", reason).with("selected", count).with("bizStatus", app.status().name())
                .with("requoted", beforePayment).with("amount", t.getAmount()));
        return viewer.detail(t, TrialActorType.OPERATOR, now);
    }

    /** 반납 받음(대여형) — 운영자 또는 광고주(광고주가 보내는 모집), 반납 사진 필수. 약속을 다 지켰으면 판정·리뷰어 단계 */
    @Transactional
    public void confirmReturn(Actor actor, String participantIdText, TrialViews.PhotosRequest body) {
        TrialParticipantEntity p = participants.findWithLockById(id(participantIdText, TrialException::participantNotFound))
                .orElseThrow(TrialException::participantNotFound);
        TrialEntity t = trials.findById(p.getTrialId()).orElseThrow(TrialException::notFound);
        ownerOrNotFound(t, actor);
        if (!actor.operator() && t.operatorShips()) {
            throw TrialException.wrongShipper(); // 운영자가 보내는 모집은 회원이 사이트로 반납한다 — 운영자가 받음
        }
        if (!t.lend()) {
            throw TrialException.notLend();
        }
        if (!p.shipped()) {
            throw TrialException.notShipped();
        }
        if (p.returned()) {
            throw TrialException.alreadyReturned();
        }
        List<TrialPhotoEntity> attach = loosePhotos(t, body == null ? null : body.photoIds(), TrialPhotoKind.RETURN, actor, true);
        Instant now = now();
        if (!p.received()) {
            p.receive(TrialParticipantEntity.RECEIVED_BY_AUTO, now, t.getReviewDays(), t.getReturnDays()); // 받음을 누르지 않고 돌려보냈다
        }
        p.confirmReturn(actor.type(), actor.id(), now);
        attach.forEach(x -> x.attach(p.getId(), null, now));
        promise.judgeIfDone(p, t, now);
        audit.record(actor.event(AuditActions.TRIAL_RETURN_CONFIRM).target("TRIAL", Long.toString(t.getId())).with("participantId", Long.toString(p.getId()))
                .with("photos", attach.size()));
    }

    /** 광고주에게 돌려보내기(운영자가 보내는 모집) — 반송 송장 */
    @Transactional
    public TrialViews.TrialDetail returnShipment(long operatorId, String ip, String idText, TrialViews.ShipmentRequest body) {
        TrialEntity t = trials.findWithLockById(id(idText, TrialException::notFound)).orElseThrow(TrialException::notFound);
        if (!t.operatorShips()) {
            throw TrialException.wrongShipper();
        }
        TrialStockReceiptEntity receipt = stocks.findByTrialId(t.getId()).orElseThrow(TrialException::noStock);
        String carrier = text(body == null ? null : body.carrier(), 30, "carrier", null);
        String tracking = text(body == null ? null : body.trackingNo(), 40, "trackingNo", null);
        Instant now = now();
        receipt.shipBack(carrier, tracking, operatorId, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.TRIAL_RETURN_SHIPMENT, AuditResult.SUCCESS).target("TRIAL", Long.toString(t.getId()))
                .with("carrier", carrier));
        return viewer.detail(t, TrialActorType.OPERATOR, now);
    }

    // ── 파손 ─────────────────────────────────────────────────────────────

    /** 파손 신고(대여형) — 운영자 또는 광고주(광고주가 보내는 모집), 사진 필수. 회원 알림(7일 안 이의) */
    @Transactional
    public TrialViews.DamageReport reportDamage(Actor actor, String participantIdText, TrialViews.DamageRequest body) {
        TrialParticipantEntity p = participants.findWithLockById(id(participantIdText, TrialException::participantNotFound))
                .orElseThrow(TrialException::participantNotFound);
        TrialEntity t = trials.findById(p.getTrialId()).orElseThrow(TrialException::notFound);
        ownerOrNotFound(t, actor);
        if (!actor.operator() && t.operatorShips()) {
            throw TrialException.wrongShipper();
        }
        if (!t.lend()) {
            throw TrialException.notLend();
        }
        if (!p.shipped()) {
            throw TrialException.notShipped();
        }
        String description = text(body == null ? null : body.description(), 1000, "description", null);
        List<TrialPhotoEntity> attach = loosePhotos(t, body == null ? null : body.photoIds(), TrialPhotoKind.DAMAGE, actor, true);
        Instant now = now();
        TrialDamageReportEntity d = TrialDamageReportEntity.report(ids.nextId(), t.getId(), p.getId(), actor.type(), actor.id(), description,
                now.plus(Duration.ofDays(OBJECTION_DAYS)), now);
        damages.persist(d);
        attach.forEach(x -> x.attach(p.getId(), d.getId(), now));
        notices.trial(p.getUserId(), MemberNotificationType.TRIAL_DAMAGE_REPORTED, t.getTitle(),
                messages.get(new MessageRef("trial.notice.damageReported", String.valueOf(OBJECTION_DAYS))), "/me/trials");
        audit.record(actor.event(AuditActions.TRIAL_DAMAGE_REPORT).target("TRIAL", Long.toString(t.getId())).with("participantId", Long.toString(p.getId()))
                .with("damageReportId", Long.toString(d.getId())).with("photos", attach.size()));
        return viewer.damage(d, viewer.member(p.getUserId(), actor.operator()), attach);
    }

    /** 파손 판정(운영자) — 회원이 이의했거나 이의 기한이 지난 뒤만(아니면 409 {@code TRIAL_DAMAGE_OBJECTION_OPEN}) */
    @Transactional
    public TrialViews.DamageReport decideDamage(long operatorId, String ip, String reportIdText, TrialViews.DecideRequest body) {
        TrialDamageReportEntity d = damages.findWithLockById(id(reportIdText, TrialException::damageNotFound)).orElseThrow(TrialException::damageNotFound);
        TrialDamageVerdict verdict = TrialDamageVerdict.parse(body == null ? null : body.verdict())
                .orElseThrow(() -> ValidationException.of("verdict", new MessageRef("trial.value.invalid")));
        String note = body.note() == null || body.note().isBlank() ? null : body.note().strip();
        if (note != null && note.length() > 1000) {
            throw ValidationException.of("note", new MessageRef("trial.text.length", "1000"));
        }
        Instant now = now();
        if (d.getStatus() == TrialDamageStatus.DECIDED) {
            throw TrialException.damageDecided();
        }
        if (d.getStatus() == TrialDamageStatus.REPORTED && now.isBefore(d.getObjectionDueAt())) {
            throw TrialException.objectionOpen();
        }
        TrialParticipantEntity p = participants.findWithLockById(d.getParticipantId()).orElseThrow(TrialException::participantNotFound);
        TrialEntity t = trials.findById(d.getTrialId()).orElseThrow(TrialException::notFound);
        d.decide(verdict, note, operatorId, now);
        promise.damageVerdict(d, p, t, verdict, operatorId, ip, now);
        notices.trial(p.getUserId(), MemberNotificationType.TRIAL_DAMAGE_DECIDED, t.getTitle(),
                messages.get(new MessageRef("trial.verdict." + verdict.name())), "/me/trials");
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.TRIAL_DAMAGE_DECIDE, AuditResult.SUCCESS).target("TRIAL", Long.toString(t.getId()))
                .with("damageReportId", Long.toString(d.getId())).with("participantId", Long.toString(p.getId())).with("verdict", verdict.name())
                .with("chargeLimit", t.getProductPrice()));
        List<TrialPhotoEntity> own = photos.findByTrialIdAndAttachedAtIsNotNullOrderByIdAsc(t.getId()).stream().filter(x -> d.getId().equals(x.getDamageReportId()))
                .toList();
        return viewer.damage(d, viewer.member(p.getUserId(), true), own);
    }

    // ── 안쪽 ─────────────────────────────────────────────────────────────

    private static void ownerOrNotFound(TrialEntity t, Actor actor) {
        try {
            requireOwner(t, actor);
        } catch (TrialException e) {
            throw TrialException.participantNotFound();
        }
    }

    private static Map<String, Object> snapshot(TrialEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", t.getStatus().name());
        m.put("advertiserId", Long.toString(t.getAdvertiserId()));
        m.put("title", t.getTitle());
        m.put("productName", t.getProductName());
        m.put("productPrice", t.getProductPrice());
        m.put("form", t.getForm().name());
        m.put("reviewBoardId", Long.toString(t.getReviewBoardId()));
        m.put("recruitCount", t.getRecruitCount());
        m.put("reviewerShare", t.getReviewerShare());
        m.put("reviewerGoldHours", t.getReviewerGoldHours());
        m.put("reviewerSilverHours", t.getReviewerSilverHours());
        m.put("applyStartAt", t.getApplyStartAt().toString());
        m.put("applyEndAt", t.getApplyEndAt().toString());
        m.put("reviewDays", t.getReviewDays());
        m.put("returnDays", t.getReturnDays());
        m.put("shipper", t.getShipper().name());
        return m;
    }

    static long id(String text, java.util.function.Supplier<TrialException> missing) {
        Long id = parseId(text);
        if (id == null) {
            throw missing.get();
        }
        return id;
    }

    static Long parseId(String text) {
        if (text == null || !text.strip().matches("[0-9]{1,19}")) {
            return null;
        }
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 앞뒤 공백을 뗀 글 1~{@code max}자 — {@code fields} 가 null 이면 바로 400 */
    private static String text(String raw, int max, String name, Map<String, MessageRef> fields) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty() || v.length() > max) {
            MessageRef ref = v.isEmpty() ? new MessageRef("trial.field.required") : new MessageRef("trial.text.length", String.valueOf(max));
            if (fields == null) {
                throw ValidationException.of(name, ref);
            }
            fields.put(name, ref);
        }
        return v;
    }

    private static int range(Integer value, Integer fallback, int min, int max, String name, Map<String, MessageRef> fields) {
        Integer v = value == null ? fallback : value;
        if (v == null || v < min || v > max) {
            fields.put(name, new MessageRef("trial.range", String.valueOf(min), String.valueOf(max)));
            return min;
        }
        return v;
    }

    /** 순간 ISO-8601 — 분 단위로 자른다(광고 신청 건 기간 규칙) */
    private static Instant instant(String raw, String name, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            fields.put(name, new MessageRef("trial.field.required"));
            return null;
        }
        try {
            return Instant.parse(raw.strip()).truncatedTo(ChronoUnit.MINUTES);
        } catch (DateTimeParseException e) {
            fields.put(name, new MessageRef("trial.value.invalid"));
            return null;
        }
    }
}

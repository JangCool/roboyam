package com.community.ad.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import com.community.ad.repository.master.AdAdvertiserStore.BusinessHit;
import com.community.audit.dto.AuditActions;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AdImage;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.service.BizApplicationNotices;
import com.community.bizapplication.service.BizApplicationOps;
import com.community.bizapplication.service.BizApplicationReviewService;
import com.community.bizapplication.service.BizFields;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizPricing;
import com.community.bizapplication.service.BizSlotService;
import com.community.bizapplication.service.BizTransitions;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 광고 일정표의 운영자 처리(ad-contract · 04-ia-review §2) — 운영자 등록(심사 요청) · 긴급 내림 · 되돌리기. 장부는 신청 건({@code biz_applications}) 그대로다.
 *
 * <ul>
 *   <li><b>운영자 등록</b>: 신청 건 {@code source=OPERATOR}·{@code SUBMITTED}·결제 방식 MANUAL + 소재 버전 1 + 자리·금액 줄(요금표 계산) + 처리 기록 {@code SUBMITTED}(행위자 운영자) +
 *       감사 {@code BIZ_APP_OPERATOR_CREATE}. 광고 심사 큐로 들어가고(운영자 알림 {@code AD_REVIEW_PENDING}) 승인 → 계약서 사본 + 입금 확인 → 확정은 신청 건 API 가 한다.
 *       심사 대기는 자리를 차지하지 않는다 — 등록 때는 잠그지 않고 안내 판정(꽉 찼으면 409 {@code BIZ_SLOT_FULL}), 승인 때 잠금 아래 다시 판정. 계약 금액은 제안({@code proposed_supply})이고
 *       승인 때 확정한다. 미리 올린 계약서 사본(선택)은 등록 때 그 건에 붙인다. 광고 종류(배너 넷·한 줄)만 — 핫딜 노출 자리는 핫딜 글이 있어야 해서 받지 않는다(400 {@code fields.type}).</li>
 *   <li><b>긴급 내림</b>: 예약(진행·시작 전, 끝나지 않은)만 → {@code TAKEN_DOWN}(까닭 EMERGENCY · 사유 필수 · 내린 운영자). 광고주 메일(선택)은 커밋 뒤 보내고 결과를 남긴다.
 *       공개 노출 캐시는 「다른 서버 사본 최대 20초」 — 노출 조회가 생기면 그 사본이 상태를 다시 읽는다(지금은 고리 없음 — 공개 노출 후속).</li>
 *   <li><b>되돌리기</b>(F5): 긴급 내림만(정지 내림은 사업자 해제가 되돌린다), 끝이 지나지 않았을 때만. 중단 시간 = 지금 − max(내린 시각, 시작)(분 단위). 보상 {@code EXTEND} = 끝을 중단 시간만큼
 *       늘린다(그 기간에 자리가 없으면 409 {@code BIZ_SLOT_FULL} — 잠금 아래 판정) · {@code PRORATE_REFUND} = 중단 일수만큼 일할 환불(환불 종류 DOWNTIME, 바로 승인 — 온라인 결제 모듈 부분 취소 ·
 *       수동 송금 대기, {@link BizPricing#downtime}). 심사 대기 환불이 있으면 일할 환불은 409(그 심사를 먼저).</li>
 * </ul>
 * 권한(등록 {@code ad.manage} · 내림 {@code ad.takedown} · 되돌리기 {@code ad.restore})은 관리자 앱 컨트롤러가 본다.
 */
@Service
public class AdOperatorService {

    /** 되돌릴 때 보상 */
    public enum Compensation {
        EXTEND, PRORATE_REFUND
    }

    /** 운영자 등록 입력 — 기간은 ISO 순간(분 단위, 반열림) */
    public record Manual(String advertiserId, String type, String startAt, String endAt, Integer weight, Creative creative, Long supply, List<String> contractFileIds,
                         String memo) {
    }

    /** 소재 — 배너는 운영자가 올린 이미지 파일 ID + 링크(https) + 대체 글, 한 줄은 문구 40자 + 링크 */
    public record Creative(String imageFileId, String text, String linkUrl, String altText) {
    }

    static final int DEFAULT_WEIGHT = 5;
    static final int CONTRACTS_MAX = 10;

    private final BizApplicationStore store;
    private final BizRefundStore refunds;
    private final BizPriceRuleStore rules;
    private final BizSlotService slots;
    private final BizApplicationOps ops;
    private final BizApplicationReviewService review;
    private final AdAdvertiserStore advertisers;
    private final AdTakedownMailer mailer;
    private final ObjectProvider<ImageStorage> images;
    private final TransactionTemplate tx;

    public AdOperatorService(BizApplicationStore store, BizRefundStore refunds, BizPriceRuleStore rules, BizSlotService slots, BizApplicationOps ops,
                             BizApplicationReviewService review, AdAdvertiserStore advertisers, AdTakedownMailer mailer, ObjectProvider<ImageStorage> images,
                             PlatformTransactionManager transactions) {
        this.store = store;
        this.refunds = refunds;
        this.rules = rules;
        this.slots = slots;
        this.ops = ops;
        this.review = review;
        this.advertisers = advertisers;
        this.mailer = mailer;
        this.images = images;
        this.tx = new TransactionTemplate(transactions);
    }

    // ── 운영자 등록 ─────────────────────────────────────────────────────────

    /** 운영자 등록 → 심사 대기 신청 건. 돌려준 값 = 신청 건 ID */
    public long createManual(BizOperator actor, Manual body) {
        Manual b = body == null ? new Manual(null, null, null, null, null, null, null, null, null) : body;
        BizFields f = BizFields.start();
        AdAdvertiserService.Ref ref = AdAdvertiserService.Ref.parse(b.advertiserId());
        if (ref == null) {
            f.add("advertiserId", BizFields.strip(b.advertiserId()) == null ? "bizapp.field.required" : "bizapp.value.invalid");
        }
        BizProduct product = f.oneOf("type", b.type(), s -> {
            BizProduct p = BizProduct.valueOf(s);
            return p.adProduct() ? p : null;
        });
        BizPeriods.Period period = period(f, b.startAt(), b.endAt());
        int weight = b.weight() == null ? DEFAULT_WEIGHT : b.weight();
        if (weight < 1 || weight > 10) {
            f.add("weight", "ad.weight.range", 1, 10);
        }
        if (b.supply() == null || b.supply() < 1) {
            f.add("supply", "bizapp.amount.invalid");
        }
        Creative c = b.creative() == null ? new Creative(null, null, null, null) : b.creative();
        Map<String, Object> content = new LinkedHashMap<>();
        String title = null;
        AdImage image = null;
        if (product == null || product.banner()) {
            Long fileId = BizFields.id(c.imageFileId());
            image = fileId == null ? null : store.operatorAdImage(fileId).orElse(null);
            if (image == null) {
                f.add("creative.imageFileId", BizFields.strip(c.imageFileId()) == null ? "bizapp.field.required" : "bizapp.image.notFound");
            }
            String link = f.link("creative.linkUrl", c.linkUrl(), true);
            title = f.text("creative.altText", c.altText(), 100);
            if (image != null) {
                content.put("imageFileId", Long.toString(image.id()));
                content.put("imageUrl", imageUrl(image.storageKey()));
            }
            content.put("linkUrl", link);
            content.put("altText", title);
        } else {
            title = f.text("creative.text", c.text(), 40);
            content.put("text", title);
            content.put("linkUrl", f.link("creative.linkUrl", c.linkUrl(), false));
        }
        String memo = BizFields.strip(b.memo());
        if (memo != null && memo.length() > 300) {
            f.add("memo", "bizapp.field.tooLong", 300);
        }
        List<Long> drafts = new ArrayList<>();
        List<String> draftTexts = b.contractFileIds() == null ? List.of() : b.contractFileIds();
        if (draftTexts.size() > CONTRACTS_MAX) {
            f.add("contractFileIds", "bizapp.file.count", CONTRACTS_MAX);
        }
        for (String text : draftTexts) {
            Long id = BizFields.id(text);
            if (id == null) {
                f.add("contractFileIds", "bizapp.contract.draftNotFound");
            } else if (!drafts.contains(id)) {
                drafts.add(id);
            }
        }
        f.throwIfAny();
        String finalTitle = title;
        AdImage usedImage = image;
        return tx.execute(status -> {
            Party party = party(ref);
            PriceRule rule = rules.find(product).orElseThrow();
            int units = BizPricing.days(period.start(), period.end());
            long quoteSupply = rule.unitPrice() * units;
            slots.require(product, period.start(), period.end(), null);
            long id = ops.nextId();
            Instant now = ops.now();
            BizApplicationKind kind = product.banner() ? BizApplicationKind.AD_BANNER : BizApplicationKind.AD_TEXT_LINE;
            store.insertOperator(new BizApplicationStore.NewOperatorApp(id, kind, party.profileId(), party.advertiserId(), party.name(), party.companyName(),
                    party.bizNoLast4(), finalTitle, period.start(), period.end(), BizPricing.amountOf(quoteSupply), weight, b.supply(), memo, actor.operatorId(), now));
            store.insertItem(new ItemRow(ops.nextId(), id, product, rule.unitPrice(), units, quoteSupply, period.start(), period.end()), now);
            store.insertCreative(ops.nextId(), id, 1, content, BizActorType.OPERATOR, actor.operatorId(), now);
            if (usedImage != null) {
                store.markImageUsed(usedImage.id(), now);
            }
            ops.event(id, BizEventAction.SUBMITTED, BizActorType.OPERATOR, actor.operatorId(), null, now);
            AppRow app = store.find(id).orElseThrow();
            int attached = review.attachDrafts(actor, app, drafts, now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("type", product.name());
            detail.put("startAt", period.start().toString());
            detail.put("endAt", period.end().toString());
            detail.put("quoteSupply", quoteSupply);
            detail.put("proposedSupply", b.supply());
            detail.put("weight", weight);
            detail.put("advertiser", b.advertiserId().strip());
            detail.put("contracts", attached);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.BIZ_APP_OPERATOR_CREATE, app, detail);
            ops.notices().reviewPending(app);
            return id;
        });
    }

    /** 광고주 쪽 — 신청 건에 남길 사업자 신원·광고주·표시 이름·상호 스냅샷 */
    private record Party(Long profileId, Long advertiserId, String name, String companyName, String bizNoLast4) {
    }

    private Party party(AdAdvertiserService.Ref ref) {
        if (ref.advertiserId() != null) {
            AdvertiserRow a = advertisers.find(ref.advertiserId()).orElseThrow(() -> ValidationException.of("advertiserId", new MessageRef("ad.advertiser.notFound")));
            return new Party(a.businessProfileId(), a.id(), a.name(), a.companyName(), a.bizNoLast4());
        }
        BusinessHit biz = advertisers.approvedBusiness(ref.businessProfileId())
                .orElseThrow(() -> ValidationException.of("advertiserId", new MessageRef("ad.advertiser.notFound")));
        Long linked = advertisers.byProfile(biz.profileId()).map(AdvertiserRow::id).orElse(null);
        return new Party(biz.profileId(), linked, biz.companyName(), biz.companyName(), biz.bizNoLast4());
    }

    /** 운영자 기간 — ISO 순간, 분 단위, 끝 > 시작, 시작은 지금 뒤 */
    private BizPeriods.Period period(BizFields f, String startText, String endText) {
        Instant start = f.instant("period", startText);
        Instant end = f.has("period") ? null : f.instant("period", endText);
        if (start == null || end == null) {
            return null;
        }
        if (!end.isAfter(start) || !BizPeriods.minuteAligned(start) || !BizPeriods.minuteAligned(end)) {
            f.add("period", "bizapp.period.invalid");
            return null;
        }
        if (!start.isAfter(ops.now())) {
            f.add("period", "bizapp.period.started");
            return null;
        }
        return new BizPeriods.Period(start, end);
    }

    // ── 긴급 내림 ───────────────────────────────────────────────────────────

    /** 긴급 내림 — 사유 1~200자. {@code notifyMail} 이면 커밋 뒤 광고주 메일(결과는 상세 {@code takedown.mail}) */
    public void takedown(BizOperator actor, long id, String reasonText, boolean notifyMail) {
        BizFields f = BizFields.start();
        String reason = f.text("reason", reasonText, 200);
        f.throwIfAny();
        AppRow app = tx.execute(status -> {
            AppRow a = store.lock(id).orElseThrow(BizApplicationException::notFound);
            Instant now = ops.now();
            BizTransitions.require(Action.TAKEDOWN, a.status());
            if (!a.endAt().isAfter(now)) {
                throw BizApplicationException.stateChanged(); // 끝난 광고는 내릴 것이 없다
            }
            if (!store.takeDownEmergency(id, reason, actor.operatorId(), now)) {
                throw BizApplicationException.stateChanged();
            }
            ops.event(id, BizEventAction.TAKEN_DOWN, BizActorType.OPERATOR, actor.operatorId(), BizTakedownCause.EMERGENCY.name(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", reason);
            detail.put("notifyMail", notifyMail);
            detail.put("phase", a.startAt().isAfter(now) ? "UPCOMING" : "RUNNING");
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.AD_TAKEDOWN, a, detail);
            ops.notices().member(a, BizApplicationNotices.Kind.TAKEN_DOWN, 0, reason);
            ops.closedAfterSchedule(a);
            return a;
        });
        if (notifyMail) {
            store.setTakedownMail(id, mailer.send(app, reason));
        }
    }

    // ── 되돌리기 ────────────────────────────────────────────────────────────

    /** 긴급 내림 되돌리기 — 보상 EXTEND·PRORATE_REFUND(400 {@code fields.compensation}), 사유 1~200자 */
    public void restore(BizOperator actor, long id, String compensationText, String reasonText) {
        BizFields f = BizFields.start();
        Compensation compensation = f.oneOf("compensation", compensationText, Compensation::valueOf);
        String reason = f.text("reason", reasonText, 200);
        f.throwIfAny();
        tx.executeWithoutResult(status -> {
            AppRow app = store.lock(id).orElseThrow(BizApplicationException::notFound);
            Instant now = ops.now();
            BizTransitions.require(Action.RESTORE, app.status());
            if (app.takedownCause() != BizTakedownCause.EMERGENCY || !app.endAt().isAfter(now)) {
                throw BizApplicationException.stateChanged(); // 정지 내림은 사업자 해제가, 끝난 광고는 되돌릴 것이 없다
            }
            Duration lost = lost(app, now);
            Instant newEnd = app.endAt();
            long refundAmount = 0;
            String note;
            if (compensation == Compensation.EXTEND) {
                newEnd = app.endAt().plus(lost);
                if (!lost.isZero()) {
                    List<BizProduct> products = store.items(id).stream().map(ItemRow::product).toList();
                    slots.lockAndRequire(products, app.startAt(), newEnd, id);
                }
                note = "EXTENDED:" + lost.toMinutes();
            } else {
                if (refunds.lockOpen(id).isPresent()) {
                    throw BizApplicationException.stateChanged(); // 심사 대기 환불을 먼저 처리한다
                }
                long paid = ops.paidAmount(app);
                BizPricing.Prorate p = BizPricing.downtime(paid, app.startAt(), app.endAt(), lost);
                refundAmount = Math.min(p.proposed(), Math.max(0, paid - refunds.approvedTotal(id)));
                if (refundAmount > 0) {
                    long refundId = ops.nextId();
                    boolean manual = app.paymentMethod() == BizPaymentMethod.MANUAL;
                    refunds.insert(new BizRefundStore.NewRefund(refundId, id, BizRefundType.DOWNTIME, BizRefundStatus.APPROVED, app.paymentMethod(), paid, refundAmount,
                            p.usedDays(), p.totalDays(), refundAmount, cut(reason, 300), manual ? BizRefundStore.TRANSFER_PENDING : null, now, now, actor.operatorId(), null));
                    if (!manual) {
                        ops.requestPgCancel(app, refundId, refundAmount, actor.operatorId());
                    }
                    ops.event(id, BizEventAction.REFUND_APPROVED, BizActorType.OPERATOR, actor.operatorId(), String.valueOf(refundAmount), now);
                }
                note = "REFUND:" + refundAmount;
            }
            if (!store.restore(id, newEnd, now)) {
                throw BizApplicationException.stateChanged();
            }
            if (!newEnd.equals(app.endAt())) {
                store.extendItems(id, newEnd);
            }
            ops.event(id, BizEventAction.RESTORED, BizActorType.OPERATOR, actor.operatorId(), note + " · " + reason, now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("compensation", compensation.name());
            detail.put("reason", reason);
            detail.put("lostMinutes", lost.toMinutes());
            detail.put("endAt", newEnd.toString());
            detail.put("refundAmount", refundAmount);
            ops.audit(actor.operatorId(), actor.ip(), AuditActions.AD_RESTORE, app, detail);
            ops.notices().member(app, BizApplicationNotices.Kind.RESTORED, 0, note);
            if (refundAmount > 0) {
                ops.notices().member(app, BizApplicationNotices.Kind.REFUND_APPROVED, refundAmount, null);
            }
            ops.scheduled(app);
        });
    }

    /** 중단 시간 — 지금 − max(내린 시각, 시작), 분 단위(시작 전에 내렸다 시작 전에 되돌리면 0) */
    static Duration lost(AppRow app, Instant now) {
        Instant from = app.takenDownAt().isAfter(app.startAt()) ? app.takenDownAt() : app.startAt();
        return now.isAfter(from) ? Duration.between(from, now).truncatedTo(ChronoUnit.MINUTES) : Duration.ZERO;
    }

    private String imageUrl(String storageKey) {
        ImageStorage storage = images.getIfAvailable();
        return storage == null ? null : storage.publicUrl(storageKey);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

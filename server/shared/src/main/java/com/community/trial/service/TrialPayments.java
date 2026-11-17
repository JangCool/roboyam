package com.community.trial.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore.TrialTerms;
import com.community.bizapplication.service.BizApplicationNotices;
import com.community.bizapplication.service.BizApplicationOps;
import com.community.bizapplication.service.BizPricing;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.bizapplication.service.BizTrialLink;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.TrialApplicationEntity;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialStatus;
import com.community.trial.exception.TrialException;
import com.community.trial.entity.TrialBizApplicationEntity;
import com.community.trial.entity.TrialPaymentTiming;
import com.community.trial.repository.master.TrialApplicationRepository;
import com.community.trial.repository.master.TrialBizApplicationRepository;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.trial.repository.master.TrialQueries;
import com.community.trial.repository.master.TrialRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 체험단 ↔ 광고 신청 건(kind TRIAL) 결제 연결(trial-group-contract 「유료 결제 연결」 — 점검 결정 D1·D7·D9 금액·L5). 광고 쪽이 보는 고리({@link BizTrialLink})의 구현이고, 체험단 처리
 * (뽑기·모집 취소·끝냄·뽑힌 줄 취소)가 신청 건을 함께 움직이는 조각이다. 모두 <b>부르는 쪽 트랜잭션 안</b>이고 잠금 차례는 결제 행 → 신청 건 행 → 모집 행이다
 * (체험단 쪽 처리는 {@link #lockFor} 로 신청 건 행을 먼저 잠근 뒤 모집 행을 잠근다).
 *
 * <ul>
 *   <li><b>결제 때</b>(V244 — 2026-11-17 사용자 결정 「선결제가 우선」): 선결제(기본)는 저장 때 <b>모집 인원</b>으로 견적을 굳히고 결제가 끝나야 모집을 연다(공개 409
 *       {@code TRIAL_PREPAY_REQUIRED}, 0원은 결제 없이). 덜 모이거나 덜 뽑혀도·뽑힌 줄을 취소해도 금액 그대로(차액 환불 없음 — 뽑기 때 다시 굳히지 않는다). 결제 뒤에는 금액에 닿는
 *       칸 변경 409 {@code TRIAL_PREPAID_LOCKED}. 공개 전 광고 쪽 만료·반려·취소면 모집은 초안 그대로, 「결제 다시 요청」이 새 신청 건을 연다. 후불은 아래 V242 규칙 그대로.</li>
 *   <li><b>견적</b>: 금액(인원당이면 인원 × 단가, 건당이면 단가, 운영자가 대신 보내면 + 인원 × 1명당 발송비 — 인원 = 선결제 모집 인원 · 후불 뽑힌 인원)을 신청 건 견적에 굳히고
 *       줄을 상품 줄(TRIAL) + 발송비 줄(TRIAL_SHIPPING)로 둔다. 금액 &gt; 0 이면 광고주 회원(사업자 신원)을 건의 회원으로 넣고(온라인 결제·사업자 알림) 운영자 심사 대기 알림.
 *       0원이면 결제 흐름 밖(심사 큐에 안 보인다).</li>
 *   <li><b>결제 전 발송 막기</b>: 금액 &gt; 0 인데 신청 건이 예약(SCHEDULED)이 아니면 입고·발송 409 {@code TRIAL_PAYMENT_PENDING}.</li>
 *   <li><b>모집 취소</b>: 결제 전이면 신청 건 취소(입금 기록이 있으면 수동 전액 환불), 결제 뒤·발송 전이면 전액 환불(온라인 PG 취소·수동 송금 대기), 발송이 있었으면 취소 불가
 *       (409 {@code TRIAL_SHIPPING_STARTED} — 모집을 끝내고 운영자 추가 환불). 신청자·뽑힌 회원 알림 · 주소 파기.</li>
 *   <li><b>모집 끝냄</b>(L5): 결제 전 건(0원 포함)은 신청 건 취소로 정리, 결제 끝 건은 닫은 시각만 찍는다. 끝내 보내지 않은 줄의 주소 사본은 바로 파기.</li>
 *   <li><b>뽑힌 줄 취소</b>(D9): 뽑힌 수는 늘 다시 세고, 금액은 결제 전(심사 대기 · 입금 기록 없는 결제·입금 대기 — 승인 뒤면 심사 대기로 되돌린다)일 때만 다시 센다. 결제 뒤면 금액 그대로.</li>
 *   <li><b>광고 쪽 → 모집</b>: 결제 기한 만료·반려·운영자 취소·환불 승인이면 모집 취소. 사업자 정지·탈퇴면 모집 전 단계(준비·모집 중·뽑는 중) 모집만 취소하고 진행 중은 그대로.</li>
 * </ul>
 */
@Component
public class TrialPayments implements BizTrialLink {

    /** 모집 전 단계 — 사업자 정지·탈퇴면 취소한다(D7) */
    static final Set<TrialStatus> BEFORE_RUNNING = EnumSet.of(TrialStatus.DRAFT, TrialStatus.RECRUITING, TrialStatus.SELECTING);
    /** 결제 전(승인 대기·결제 대기·입금 대기) */
    private static final Set<BizApplicationStatus> UNPAID = EnumSet.of(BizApplicationStatus.SUBMITTED, BizApplicationStatus.AWAITING_PAYMENT,
            BizApplicationStatus.AWAITING_DEPOSIT);

    private final TrialRepository trials;
    private final TrialApplicationRepository applications;
    private final TrialParticipantRepository participants;
    private final TrialQueries queries;
    private final BizApplicationStore store;
    private final ObjectProvider<BizApplicationOps> ops;
    private final AdAdvertiserStore advertisers;
    private final BusinessProfileRepository profiles;
    private final MemberNotificationPublisher notices;
    private final AuditLogService audit;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;
    private final TrialBizApplicationRepository links;

    public TrialPayments(TrialRepository trials, TrialApplicationRepository applications, TrialParticipantRepository participants, TrialQueries queries,
                         BizApplicationStore store, ObjectProvider<BizApplicationOps> ops, AdAdvertiserStore advertisers, BusinessProfileRepository profiles,
                         MemberNotificationPublisher notices, AuditLogService audit, Messages messages, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks,
                         TrialBizApplicationRepository links) {
        this.links = links;
        this.trials = trials;
        this.applications = applications;
        this.participants = participants;
        this.queries = queries;
        this.store = store;
        this.ops = ops;
        this.advertisers = advertisers;
        this.profiles = profiles;
        this.notices = notices;
        this.audit = audit;
        this.messages = messages;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    private BizApplicationOps ops() {
        return ops.getObject();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // ── 잠금 ─────────────────────────────────────────────────────────────

    /** 잠근 모집과 그 신청 건 */
    public record Locked(TrialEntity trial, AppRow app) {
    }

    /**
     * 체험단 쪽 처리(모집 취소·끝냄·뽑힌 줄 취소)의 잠금 — 열린 결제(READY)를 먼저 닫고 신청 건 행 → 모집 행 차례로 잠근다(광고 쪽 처리·결제 확정과 같은 차례).
     * 모집 ID 는 엔티티를 올리지 않는 칸 읽기로 꺼낸다(잠그기 전 낡은 엔티티가 영속성 문맥에 남지 않게).
     */
    public Locked lockFor(long trialId, String reason) {
        long appId = queries.bizApplicationIdOf(trialId).orElseThrow(TrialException::notFound);
        AppRow app = ops().closePaymentsThenLock(appId, null, reason);
        TrialEntity t = trials.findWithLockById(trialId).orElseThrow(TrialException::notFound);
        return new Locked(t, app);
    }

    // ── 견적 ─────────────────────────────────────────────────────────────

    /** 견적 줄 — 상품 줄(TRIAL: 인원당 단가 × 인원 · 건당 단가 × 1) + 운영자가 대신 보낼 때 발송비 줄(TRIAL_SHIPPING: 1명당 발송비 × 인원, 0원이면 줄 없음) */
    List<ItemRow> lines(TrialEntity t, int count) {
        long appId = t.getBizApplicationId();
        List<ItemRow> out = new ArrayList<>();
        boolean perRecruit = TrialTerms.PER_RECRUIT.equals(t.getPriceUnit());
        // 줄은 수량 ≥ 1(ck_bai_amount) — 인원당인데 남은 인원이 0 이면 단가 0 × 1 로 금액 0 을 둔다
        int units = perRecruit ? 1 : Math.max(1, count);
        long unitPrice = !perRecruit && count == 0 ? 0 : t.getUnitPrice();
        out.add(new ItemRow(ids.nextId(), appId, BizProduct.TRIAL, unitPrice, units, unitPrice * units, t.getApplyStartAt(), t.getApplyEndAt()));
        if (t.operatorShips() && t.getShippingFeePerPerson() > 0 && count > 0) {
            out.add(new ItemRow(ids.nextId(), appId, BizProduct.TRIAL_SHIPPING, t.getShippingFeePerPerson(), count, t.getShippingFeePerPerson() * count,
                    t.getApplyStartAt(), t.getApplyEndAt()));
        }
        return out;
    }

    /** 확정 금액(공급가) — 요금표 사본(모집을 만들 때의 값)으로 */
    static long amountOf(TrialEntity t, int count) {
        return new TrialTerms(t.getPriceUnit(), t.getUnitPrice(), t.getShippingFeePerPerson(), true).amount(count, t.operatorShips());
    }

    /**
     * 견적을 신청 건에 굳힌다(뽑기 · 결제 전 다시 셈) — 신청 건이 심사 대기여야 한다(아니면 409 {@code TRIAL_STATE_INVALID}). 금액 &gt; 0 이면 광고주 회원을 넣고 운영자 심사 대기 알림.
     *
     * @param requote 다시 셈(처리 기록 TRIAL_REQUOTED) · 아니면 뽑기(TRIAL_QUOTED)
     */
    public BizPricing.Amount quote(TrialEntity t, int count, long amount, long operatorId, boolean requote, Instant now) {
        BizPricing.Amount q = BizPricing.amountOf(amount);
        Long member = amount > 0 ? advertiserMember(t.getAdvertiserId()) : null;
        if (!store.quoteTrial(t.getBizApplicationId(), q, member, now)) {
            throw TrialException.stateInvalid();
        }
        store.replaceItems(t.getBizApplicationId(), lines(t, count), now);
        ops().event(t.getBizApplicationId(), requote ? BizEventAction.TRIAL_REQUOTED : BizEventAction.TRIAL_QUOTED, BizActorType.OPERATOR, operatorId,
                String.valueOf(q.total()), now);
        if (amount > 0) {
            store.find(t.getBizApplicationId()).ifPresent(app -> ops().notices().reviewPending(app)); // 광고 심사 큐 「승인 대기」
        }
        return q;
    }

    /** 광고주에 이어진 사업자 신원의 회원(지운 신원·이어지지 않은 광고주는 null — 수동 결제만) */
    Long advertiserMember(long advertiserId) {
        return advertisers.find(advertiserId).map(AdAdvertiserStore.AdvertiserRow::businessProfileId).flatMap(profiles::findById)
                .filter(p -> p.getErasedAt() == null).map(BusinessProfileEntity::getUserId).orElse(null);
    }

    // ── 신청 건 열기 · 선결제(V244) ──────────────────────────────────────────

    /**
     * 새 광고 신청 건(kind TRIAL · 운영자 등록 · 견적 0) + 상품 줄(TRIAL 단가 × 1) — 모집 만들기 · 「결제 다시 요청」. 이력({@link #link})은 모집 행이 생긴 뒤 부르는 쪽이 남긴다.
     */
    public long openApplication(long advertiserId, String title, Instant startAt, Instant endAt, long unitPrice, long operatorId, Instant now) {
        AdAdvertiserStore.AdvertiserRow adv = advertisers.find(advertiserId).orElseThrow(TrialException::notFound);
        long appId = ids.nextId();
        store.insertTrial(appId, adv.businessProfileId(), adv.id(), adv.name(), adv.companyName(), adv.bizNoLast4(), title, startAt, endAt, operatorId, now);
        store.insertItem(new ItemRow(ids.nextId(), appId, BizProduct.TRIAL, unitPrice, 1, unitPrice, startAt, endAt), now);
        return appId;
    }

    /** 모집 ↔ 신청 건 이력 한 줄(V244 — 예전 건도 어느 모집의 건인지 찾는다) */
    public void link(long bizApplicationId, long trialId, Instant now) {
        links.persist(TrialBizApplicationEntity.of(bizApplicationId, trialId, now));
    }

    /** 결제가 끝났나 — 예약·내림, 또는 입금 기록이 있는 입금 대기(돈이 들어왔다 — 금액을 다시 셀 수 없다) */
    public boolean paid(AppRow app) {
        return app.status() == BizApplicationStatus.SCHEDULED || app.status() == BizApplicationStatus.TAKEN_DOWN
                || (app.status() == BizApplicationStatus.AWAITING_DEPOSIT && store.deposit(app.id()).isPresent());
    }

    /**
     * 선결제 견적 맞추기(잠근 모집 · 잠근 신청 건 — 만들기·고치기·결제 요청) — 금액 = 모집 인원으로 센 값({@link #amountOf}).
     * <ul>
     *   <li>신청 건이 닫혔으면(만료·반려·취소) 금액만 두고 「결제 다시 요청」 때 새 건에 굳힌다.</li>
     *   <li>결제가 끝났으면 금액이 바뀌는 고치기 409 {@code TRIAL_PREPAID_LOCKED}(차액 정산 없음). 연 모집(초안 아님)도 금액이 바뀌면 409(0원으로 연 모집이 유료가 되지 않게).</li>
     *   <li>결제 전이면 다시 센다 — 승인 뒤(결제·입금 대기, 입금 기록 없음)면 심사 대기로 되돌린다(열린 결제는 잠글 때 닫았다). 0원 → 0원은 처리 기록 없이 줄만.</li>
     * </ul>
     */
    public void syncPrepaid(TrialEntity t, AppRow app, long operatorId, Instant now) {
        int count = t.getRecruitCount();
        long amount = amountOf(t, count);
        Long before = t.getAmount();
        boolean changed = before == null || before != amount;
        if (BizApplicationStatus.CLOSED.contains(app.status())) {
            t.prepay(amount, now);
            return;
        }
        if (changed && (paid(app) || t.getStatus() != TrialStatus.DRAFT)) {
            throw TrialException.prepaidLocked();
        }
        if (!changed) {
            return;
        }
        t.prepay(amount, now);
        if (app.status() == BizApplicationStatus.SUBMITTED && app.quote().total() == 0 && amount == 0) {
            store.replaceItems(app.id(), lines(t, count), now); // 0원 — 결제 흐름 밖(심사 큐·처리 기록 없이 줄만)
            return;
        }
        if (BizApplicationStatus.AWAITING.contains(app.status()) && !store.backToReview(app.id(), app.status(), app.title(), now)) {
            throw TrialException.stateInvalid();
        }
        quote(t, count, amount, operatorId, before != null, now);
    }

    /**
     * 선결제 → 후불(초안 · 잠근 신청 건) — 결제가 끝났으면 409 {@code TRIAL_PREPAID_LOCKED}. 닫힌 건이면 새 건을 열고, 아니면 견적을 0 으로 거둔다(승인 뒤면 심사 대기로 되돌린 뒤 —
     * 처리 기록 TRIAL_REQUOTED 0). 금액은 뽑기 때 굳힌다.
     */
    public void toPostpaid(TrialEntity t, AppRow app, long operatorId, Instant now) {
        if (paid(app)) {
            throw TrialException.prepaidLocked();
        }
        t.changeTiming(TrialPaymentTiming.POSTPAID, now);
        if (BizApplicationStatus.CLOSED.contains(app.status())) {
            reopen(t, app, operatorId, now);
            return;
        }
        if (BizApplicationStatus.AWAITING.contains(app.status()) && !store.backToReview(app.id(), app.status(), app.title(), now)) {
            throw TrialException.stateInvalid();
        }
        if (!store.quoteTrial(app.id(), BizPricing.amountOf(0), null, now)) {
            throw TrialException.stateInvalid();
        }
        store.replaceItems(app.id(), List.of(new ItemRow(ids.nextId(), app.id(), BizProduct.TRIAL, t.getUnitPrice(), 1, t.getUnitPrice(), t.getApplyStartAt(),
                t.getApplyEndAt())), now);
        if (app.quote().total() > 0) {
            ops().event(app.id(), BizEventAction.TRIAL_REQUOTED, BizActorType.OPERATOR, operatorId, "0", now);
        }
    }

    /**
     * 「결제 다시 요청」({@code PUT /api/admin/trials/{id}} {@code {action: REQUEST_PAYMENT}} — 선결제 초안만, 아니면 409 {@code TRIAL_STATE_INVALID}).
     * 신청 건이 닫혔으면(만료·반려·취소) 새 신청 건을 열어 지금 결제 건으로 잇고 견적을 굳힌다. 견적 전(V244 전 초안)이면 지금 건에 굳힌다. 이미 요청했으면 409.
     *
     * @return 새로 연 신청 건 ID(지금 건에 굳혔으면 null)
     */
    public Long requestPayment(TrialEntity t, AppRow app, long operatorId, Instant now) {
        if (!t.prepaid() || t.getStatus() != TrialStatus.DRAFT) {
            throw TrialException.stateInvalid();
        }
        if (BizApplicationStatus.CLOSED.contains(app.status())) {
            return reopen(t, app, operatorId, now);
        }
        if (t.getAmount() == null && app.status() == BizApplicationStatus.SUBMITTED) {
            syncPrepaid(t, app, operatorId, now);
            return null;
        }
        throw TrialException.stateInvalid();
    }

    /** 닫힌 건 대신 새 신청 건을 열어 잇는다 — 선결제면 모집 인원으로 견적을 굳힌다(0원이면 줄만) */
    private long reopen(TrialEntity t, AppRow closed, long operatorId, Instant now) {
        long appId = openApplication(t.getAdvertiserId(), t.getTitle(), t.getApplyStartAt(), t.getApplyEndAt(), t.getUnitPrice(), operatorId, now);
        link(appId, t.getId(), now);
        t.relink(appId, now);
        if (t.prepaid()) {
            int count = t.getRecruitCount();
            long amount = amountOf(t, count);
            t.prepay(amount, now);
            if (amount > 0) {
                quote(t, count, amount, operatorId, false, now);
            } else {
                store.replaceItems(appId, lines(t, count), now);
            }
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousBizApplicationId", Long.toString(closed.id()));
        detail.put("previousBizStatus", closed.status().name());
        detail.put("bizApplicationId", Long.toString(appId));
        detail.put("paymentTiming", t.getPaymentTiming().name());
        detail.put("amount", t.getAmount());
        audit.record(new AuditEvent(AuditActorType.OPERATOR, operatorId, AuditActions.TRIAL_PAYMENT_REQUEST, AuditResult.SUCCESS, "TRIAL", Long.toString(t.getId()), null,
                detail));
        return appId;
    }

    /** 선결제 공개 검사 — 견적 전이면, 금액 &gt; 0 인데 결제(예약)가 끝나지 않았으면 409 {@code TRIAL_PREPAY_REQUIRED}. 0원·후불은 막지 않는다 */
    public void requirePublishable(TrialEntity t) {
        if (!t.prepaid()) {
            return;
        }
        if (t.getAmount() == null) {
            throw TrialException.prepayRequired();
        }
        if (t.getAmount() == 0) {
            return;
        }
        AppRow app = store.find(t.getBizApplicationId()).orElseThrow(TrialException::notFound);
        if (app.status() != BizApplicationStatus.SCHEDULED) {
            throw TrialException.prepayRequired();
        }
    }

    // ── 결제 상태 ─────────────────────────────────────────────────────────

    /** 결제가 끝나야 하는데 아직이면 409 {@code TRIAL_PAYMENT_PENDING} — 0원·뽑기 전(입고·발송은 뽑기 뒤라 해당 없음)은 막지 않는다 */
    public void requirePaid(TrialEntity t) {
        if (t.getAmount() == null || t.getAmount() <= 0) {
            return;
        }
        AppRow app = store.find(t.getBizApplicationId()).orElseThrow(TrialException::notFound);
        if (app.status() != BizApplicationStatus.SCHEDULED) {
            throw TrialException.paymentPending();
        }
    }

    /** 모집 상세의 결제 칸 */
    public TrialViews.Payment view(TrialEntity t, AppRow app, Instant now) {
        if (app == null) {
            return null;
        }
        boolean selected = t.getAmount() != null;
        boolean required = selected && t.getAmount() > 0;
        String status;
        if (t.prepaid()) {
            status = prepaidStatus(app, selected, required);
        } else if (!selected) {
            status = "BEFORE_SELECTION";
        } else if (!required && app.status() == BizApplicationStatus.SUBMITTED) {
            status = "NOT_REQUIRED";
        } else {
            status = switch (app.status()) {
                case SUBMITTED -> app.quote().total() > 0 ? "AWAITING_APPROVAL" : "NOT_REQUIRED";
                case AWAITING_PAYMENT -> "AWAITING_PAYMENT";
                case AWAITING_DEPOSIT -> "AWAITING_DEPOSIT";
                case SCHEDULED, TAKEN_DOWN -> "PAID";
                default -> required ? "CANCELED" : "NOT_REQUIRED";
            };
        }
        BizPricing.Amount shown = app.approved() != null ? app.approved() : app.quote();
        boolean payOnline = app.status() == BizApplicationStatus.AWAITING_PAYMENT && app.paymentMethod() == BizPaymentMethod.ONLINE && app.userId() != null
                && app.paymentDeadlineAt() != null && app.paymentDeadlineAt().isAfter(now);
        boolean paidStatus = "PAID".equals(status);
        boolean publishAllowed = !t.prepaid() || (selected && (!required || paidStatus));
        boolean requestAllowed = t.prepaid() && t.getStatus() == TrialStatus.DRAFT
                && ((!selected && app.status() == BizApplicationStatus.SUBMITTED) || BizApplicationStatus.CLOSED.contains(app.status()));
        return new TrialViews.Payment(t.getPaymentTiming().name(), required, status, !required || paidStatus, publishAllowed, requestAllowed, Long.toString(app.id()),
                app.no(), app.status().name(), shown.supply(), shown.vat(), shown.total(),
                app.paymentMethod() == null || app.approved() == null ? null : app.paymentMethod().name(), app.deadlineAt(), app.paidAt(), payOnline);
    }

    /** 선결제 결제 상태 — 견적 전 · 0원 · 승인·결제·입금 대기 · 결제 끝 · 닫힘(만료·반려·취소 — 공개 전이면 다시 요청) */
    private static String prepaidStatus(AppRow app, boolean quoted, boolean required) {
        if (!quoted) {
            return "AWAITING_QUOTE_REQUEST";
        }
        if (!required) {
            return "NOT_REQUIRED";
        }
        return switch (app.status()) {
            case SUBMITTED -> "AWAITING_APPROVAL";
            case AWAITING_PAYMENT -> "AWAITING_PAYMENT";
            case AWAITING_DEPOSIT -> "AWAITING_DEPOSIT";
            case SCHEDULED, TAKEN_DOWN -> "PAID";
            case EXPIRED -> "EXPIRED";
            case REJECTED -> "REJECTED";
            case CANCELED -> "CANCELED";
        };
    }

    /** 목록 줄의 결제 상태(상세 {@code payment.status} 와 같은 값) */
    public String status(TrialEntity t, AppRow app, Instant now) {
        TrialViews.Payment p = view(t, app, now);
        return p == null ? null : p.status();
    }

    // ── 모집 취소 · 끝냄 ───────────────────────────────────────────────────

    /**
     * 모집 취소(잠근 모집) — 준비·모집 중·뽑는 중·진행 중(발송 전)만(아니면 409 {@code TRIAL_STATE_INVALID}, 발송이 있었으면 409 {@code TRIAL_SHIPPING_STARTED}).
     * 신청 대기 = 안 뽑힘(주소 파기 · 알림 TRIAL_NOT_SELECTED 「모집이 취소됐어요」) · 뽑힌 줄 = 취소(주소 파기 · 알림 TRIAL_CANCELLED_BY_OPERATOR).
     *
     * @param app 잠근 신청 건 — 광고 쪽에서 이미 닫았으면({@code fromBiz}) null 로 부르고 신청 건은 건드리지 않는다
     * @return 돌려준 결제액(전액 환불이면 결제액, 아니면 0)
     */
    public long cancel(TrialEntity t, AppRow app, String reason, Long operatorId, boolean fromBiz, Instant now) {
        TrialStatus s = t.getStatus();
        if (s != TrialStatus.DRAFT && s != TrialStatus.RECRUITING && s != TrialStatus.SELECTING && s != TrialStatus.RUNNING) {
            throw TrialException.stateInvalid();
        }
        if (s == TrialStatus.RUNNING && queries.anyStarted(t.getId())) {
            throw fromBiz ? BizApplicationException.stateChanged() : TrialException.shippingStarted();
        }
        t.changeStatus(TrialStatus.CANCELLED, now);
        String note = messages.get(new MessageRef("trial.notice.cancelled"));
        for (TrialApplicationEntity a : applications.findByTrialIdOrderByCreatedAtAscIdAsc(t.getId())) {
            if (a.getStatus() == TrialApplicationStatus.APPLIED) {
                a.notSelect(now);
                notices.trial(a.getUserId(), MemberNotificationType.TRIAL_NOT_SELECTED, t.getTitle(), note, "/me/trials");
            } else {
                a.purgeAddress(now); // 뽑힌 줄의 주소도 쓸 일이 없다
            }
        }
        for (TrialParticipantEntity p : participants.findByTrialIdOrderByIdAsc(t.getId())) {
            if (!p.cancelled()) {
                p.cancel(operatorId, reason, now);
                notices.trial(p.getUserId(), MemberNotificationType.TRIAL_CANCELLED_BY_OPERATOR, t.getTitle(), note, "/me/trials");
            }
        }
        long refunded = 0;
        if (!fromBiz && app != null) {
            BizApplicationOps o = ops();
            BizActorType actor = operatorId == null ? BizActorType.SYSTEM : BizActorType.OPERATOR;
            if (UNPAID.contains(app.status())) {
                refunded = app.status() == BizApplicationStatus.AWAITING_DEPOSIT ? store.deposit(app.id()).map(BizApplicationStore.DepositRow::amount).orElse(0L) : 0;
                o.cancelBeforePayment(app, operatorId == null ? Action.SYSTEM_CANCEL : Action.OPERATOR_CANCEL, actor, operatorId, reason, now);
                o.notices().member(app, BizApplicationNotices.Kind.CANCELED, refunded, null);
            } else if (app.status() == BizApplicationStatus.SCHEDULED) {
                refunded = o.paidAmount(app);
                o.cancelWithFullRefund(app, actor, operatorId, reason, now); // 결제 뒤·발송 전 — 전액 환불(일할 없음)
                o.notices().member(app, BizApplicationNotices.Kind.REFUND_APPROVED, refunded, null);
            }
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", reason);
        detail.put("from", s.name());
        detail.put("bizApplicationId", Long.toString(t.getBizApplicationId()));
        detail.put("bizStatus", app == null ? null : app.status().name());
        detail.put("refund", refunded);
        detail.put("fromBiz", fromBiz);
        audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, AuditActions.TRIAL_CANCEL, AuditResult.SUCCESS,
                "TRIAL", Long.toString(t.getId()), null, detail));
        return refunded;
    }

    /**
     * 모집 끝냄(잠근 모집 — 진행 중만, 부르는 쪽이 본다) — L5: 결제 전 건(0원·승인 대기·결제·입금 대기)은 신청 건 취소(입금 기록이 있으면 수동 전액 환불)로 정리, 결제 끝 건은 닫은 시각만.
     * 끝내 보내지 않은 뽑힌 줄의 주소 사본은 바로 파기(D9).
     */
    public void close(TrialEntity t, AppRow app, Long operatorId, Instant now) {
        t.changeStatus(TrialStatus.CLOSED, now);
        BizApplicationOps o = ops();
        BizActorType actor = operatorId == null ? BizActorType.SYSTEM : BizActorType.OPERATOR;
        if (UNPAID.contains(app.status())) {
            long refunded = app.status() == BizApplicationStatus.AWAITING_DEPOSIT ? store.deposit(app.id()).map(BizApplicationStore.DepositRow::amount).orElse(0L) : 0;
            o.cancelBeforePayment(app, Action.SYSTEM_CANCEL, actor, operatorId, "TRIAL_CLOSED", now);
            o.notices().member(app, BizApplicationNotices.Kind.CANCELED, refunded, null);
        } else if (store.markTrialClosed(app.id(), now)) {
            o.event(app.id(), BizEventAction.TRIAL_CLOSED, actor, operatorId, null, now);
        }
        for (TrialParticipantEntity p : participants.findByTrialIdOrderByIdAsc(t.getId())) {
            if (!p.shipped()) {
                applications.findWithLockById(p.getApplicationId()).ifPresent(a -> a.purgeAddress(now));
            }
        }
    }

    // ── 광고 쪽 고리(BizTrialLink) ─────────────────────────────────────────

    @Override
    public Optional<TrialInfo> info(long bizApplicationId) {
        return queries.trialIdOfBizApplication(bizApplicationId).flatMap(trials::findById)
                .map(t -> new TrialInfo(t.getId(), t.getTitle(), t.getStatus().name(), t.getProductName(), t.getForm().name(), t.getShipper().name(), t.getSelectedCount(),
                        t.getPriceUnit(), t.getUnitPrice(), t.getShippingFeePerPerson(), t.getAmount(), t.getPaymentTiming().name(), t.getRecruitCount(),
                        t.getBizApplicationId() == bizApplicationId));
    }

    @Override
    public boolean shippingStarted(long bizApplicationId) {
        return queries.trialIdOfBizApplication(bizApplicationId).map(queries::anyStarted).orElse(false);
    }

    @Override
    public void bizClosed(long bizApplicationId, String reason, Long operatorId) {
        Optional<Long> trialId = queries.trialIdOfBizApplication(bizApplicationId);
        if (trialId.isEmpty()) {
            return;
        }
        TrialEntity t = trials.findWithLockById(trialId.get()).orElseThrow(TrialException::notFound);
        if (t.getStatus() == TrialStatus.CLOSED || t.getStatus() == TrialStatus.CANCELLED) {
            return;
        }
        if (t.getBizApplicationId() != bizApplicationId) {
            return; // 「결제 다시 요청」 전의 예전 건 — 지금 결제 건이 아니다
        }
        if (t.prepaid() && t.getStatus() == TrialStatus.DRAFT) {
            return; // 선결제 공개 전(V244) — 모집은 초안 그대로, 결제 상태만 만료·반려·취소로 보이고 「결제 다시 요청」을 할 수 있다
        }
        cancel(t, null, reason, operatorId, true, now());
    }

    @Override
    public TrialCounts counts(long businessProfileId) {
        int recruiting = queries.byBusinessProfile(businessProfileId, BEFORE_RUNNING).size();
        int running = queries.byBusinessProfile(businessProfileId, EnumSet.of(TrialStatus.RUNNING)).size();
        return new TrialCounts(recruiting, running);
    }

    @Override
    public void businessEnded(long businessProfileId, String reason, Long operatorId) {
        Instant now = now();
        for (Long trialId : queries.byBusinessProfile(businessProfileId, BEFORE_RUNNING)) {
            Locked l = lockFor(trialId, "TRIAL_BUSINESS_" + reason);
            if (BEFORE_RUNNING.contains(l.trial().getStatus())) {
                cancel(l.trial(), l.app(), reason, operatorId, false, now);
            }
        }
    }

    /** 신청 건의 종류가 체험단인가(시험·문서용) */
    static boolean trialKind(AppRow app) {
        return app.kind() == BizApplicationKind.TRIAL;
    }
}

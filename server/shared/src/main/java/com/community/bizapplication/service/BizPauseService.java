package com.community.bizapplication.service;

import com.community.audit.dto.AuditActions;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPauseChannel;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizApplicationStore.PauseActor;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.NewStopRefund;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.business.service.BusinessEligibility;
import com.community.post.entity.PostEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 광고주 요청 일시 정지·다시 켜기·중단(V127 — docs/features/biz-pause-contract.md 규칙 1~11). 사업자(user-web)와 운영자 대신 처리(admin-web — 요청 경로 + 메모 필수)가 같은 규칙을 쓴다.
 * 처리는 신청 건 행을 잠그고 상태를 다시 본다 — 아니면 409 {@code BIZ_APPLICATION_STATE_CHANGED}. 처리 기록·감사·알림·핫딜 글 고리는 같은 트랜잭션.
 *
 * <ul>
 *   <li><b>정지</b>: 진행 중 예약(시작 ≤ 지금 &lt; 끝)·열린 환불 없음 · 사업자 핫딜 글은 게시되고 보이며 딜 진행 중일 때만. 정지 횟수 한도(설정 {@code irobit.biz.pause-limit}, 기본 3 —
 *       정지할 때 센다, 넘으면 409 {@code BIZ_PAUSE_LIMIT}) → {@code TAKEN_DOWN}·{@code ADVERTISER_PAUSE}. 자리는 계속 그 광고주 몫(내림이 자리를 차지한다), 공개 노출은 예약만 내므로 빠진다.</li>
 *   <li><b>다시 켜기</b>: 정지 중이고 끝 전만 → {@code SCHEDULED}(승인 없이 · 소재 그대로 · <b>끝 시각을 늘리지 않는다</b> · 멈춘 분을 {@code paused_minutes} 에 더한다).
 *       사업자가 직접 켤 때는 자격을 다시 본다(정지된 사업자는 409 {@code BUSINESS_NOT_APPROVED} — 가정).</li>
 *   <li><b>중단</b>: 진행 중 예약 또는 정지 중(끝 전)·열린 환불 없음 → {@code ADVERTISER_STOP}(되돌리기 없음). 일할 기준 시각 = 정지 중 전환이면 멈춘 시각, 아니면 중단 시각.
 *       환불 예상액 = 일할 − 중도 해지 수수료({@link BizPricing#stop} — 비율은 신청 건에 고정된 값)가 0 보다 크면 {@code STOP_PRORATED} 심사 대기 + 운영자 알림, 0 이면 환불 줄 없음.
 *       사업자 핫딜 글은 게시비({@code HOTDEAL_POST} 줄)를 빼고 노출 자리 줄만 대상이다.</li>
 * </ul>
 */
@Service
public class BizPauseService {

    /** 정지·중단할 수 있는 종류 — 날짜로 산 자리(광고 둘)와 사업자 핫딜 글. 게시된 글에 노출만 붙인 건은 공개 신청이 없어 뺀다 */
    static final Set<BizApplicationKind> KINDS = EnumSet.of(BizApplicationKind.AD_BANNER, BizApplicationKind.AD_TEXT_LINE, BizApplicationKind.HOTDEAL_POST);

    /** 처리 기록 note 토큰 — 정지 중 전환 · 멈춘 분 */
    public static final String FROM_PAUSE = "FROM_PAUSE";
    public static final String MINUTES_PREFIX = "MIN:";
    /** 중단 환불 요청 기록의 note(= 내림 까닭) */
    public static final String STOP_NOTE = BizTakedownCause.ADVERTISER_STOP.name();

    /** 상세 {@code pause} 의 재료 — 화면 모양은 각 앱이 만든다(공개는 이름을 싣지 않는다) */
    public record PauseInfo(int used, int limit, Instant pausedAt, BizActorType pausedBy, BizPauseChannel channel, int pausedMinutes, Instant stoppedAt,
                            Instant refundFrom, long proratableTotal, int feeRate, String actorName) {
    }

    /** 누가 — 사업자(회원 ID) 또는 운영자 대신(운영자 · 요청 경로 · 메모) */
    private record Who(BizActorType type, Long userId, BizOperator operator, BizPauseChannel channel, String memo) {

        boolean byOperator() {
            return type == BizActorType.OPERATOR;
        }

        Long actorId() {
            return byOperator() ? operator.operatorId() : userId;
        }

        PauseActor actor() {
            return new PauseActor(type, byOperator() ? operator.operatorId() : null, channel, memo);
        }

        /** 운영자 대신이면 {@code 경로 · 메모} 토큰 */
        List<String> tokens() {
            return byOperator() ? List.of(channel.name(), memo) : List.of();
        }
    }

    private final BizApplicationStore store;
    private final BizRefundStore refunds;
    private final BizApplicationOps ops;
    private final BizApplicationProperties props;
    private final BizDealPostReader dealPosts;
    private final BusinessEligibility eligibility;
    private final TransactionTemplate tx;

    public BizPauseService(BizApplicationStore store, BizRefundStore refunds, BizApplicationOps ops, BizApplicationProperties props, BizDealPostReader dealPosts,
                           BusinessEligibility eligibility, PlatformTransactionManager transactions) {
        this.store = store;
        this.refunds = refunds;
        this.ops = ops;
        this.props = props;
        this.dealPosts = dealPosts;
        this.eligibility = eligibility;
        this.tx = new TransactionTemplate(transactions);
    }

    // ── 사업자 ─────────────────────────────────────────────────────────────

    public void pause(long userId, long id) {
        tx.executeWithoutResult(s -> pauseLocked(member(userId), store.lockOwned(id, userId).orElseThrow(BizApplicationException::notFound)));
    }

    public void resume(long userId, long id) {
        tx.executeWithoutResult(s -> resumeLocked(member(userId), store.lockOwned(id, userId).orElseThrow(BizApplicationException::notFound)));
    }

    /** 중단 — 사유 1~300자({@code fields.reason}) */
    public void stop(long userId, long id, String reasonText) {
        BizFields f = BizFields.start();
        String reason = f.text("reason", reasonText, 300);
        f.throwIfAny();
        tx.executeWithoutResult(s -> stopLocked(member(userId), store.lockOwned(id, userId).orElseThrow(BizApplicationException::notFound), reason));
    }

    /**
     * 진행 중 취소·환불 요청(옛 길)을 중단으로 — 부르는 쪽({@link BizApplicationMemberService#requestRefund})의 트랜잭션 안에서, 그 건 행을 잠근 뒤.
     */
    void stopFromRefundRequest(long userId, AppRow locked, String reason) {
        stopLocked(member(userId), locked, reason);
    }

    // ── 운영자 대신 처리(요청 경로 + 메모 필수 — 400 fields.channel·fields.memo) ──

    public void pauseByOperator(BizOperator actor, long id, String channelText, String memoText) {
        Who who = operator(actor, channelText, memoText);
        tx.executeWithoutResult(s -> pauseLocked(who, store.lock(id).orElseThrow(BizApplicationException::notFound)));
    }

    public void resumeByOperator(BizOperator actor, long id, String channelText, String memoText) {
        Who who = operator(actor, channelText, memoText);
        tx.executeWithoutResult(s -> resumeLocked(who, store.lock(id).orElseThrow(BizApplicationException::notFound)));
    }

    /** 운영자 대신 중단 — 사유는 선택(300자 — 사업자 화면에 보인다). 메모는 관리자에게만(사유 칸에 옮기지 않는다) */
    public void stopByOperator(BizOperator actor, long id, String channelText, String memoText, String reasonText) {
        BizFields f = BizFields.start();
        BizPauseChannel channel = f.oneOf("channel", channelText, BizPauseChannel::valueOf);
        String memo = f.text("memo", memoText, 200);
        String reason = BizFields.strip(reasonText);
        if (reason != null && reason.length() > 300) {
            f.add("reason", "bizapp.field.tooLong", 300);
        }
        f.throwIfAny();
        Who who = new Who(BizActorType.OPERATOR, null, actor, channel, memo);
        tx.executeWithoutResult(s -> stopLocked(who, store.lock(id).orElseThrow(BizApplicationException::notFound), reason));
    }

    // ── 읽기 ───────────────────────────────────────────────────────────────

    /** 상세 {@code pause} — 정지·중단할 수 있는 종류의 결제 뒤 건(예약·내림, 중단 뒤 환불 승인으로 닫힌 건 포함)만, 아니면 null */
    public PauseInfo info(AppRow app, List<ItemRow> items) {
        boolean after = app.status() == BizApplicationStatus.SCHEDULED || app.status() == BizApplicationStatus.TAKEN_DOWN
                || (app.status() == BizApplicationStatus.CANCELED && app.takedownCause() != null && app.takedownCause().advertiser());
        if (!KINDS.contains(app.kind()) || !after) {
            return null;
        }
        boolean paused = app.paused();
        boolean advertiser = app.takedownCause() != null && app.takedownCause().advertiser();
        String actorName = null;
        if (paused) {
            actorName = app.takedownActor() == BizActorType.OPERATOR ? store.operatorName(app.takenDownBy()) : nameOf(app);
        }
        return new PauseInfo(app.pauseCount(), props.pauseLimit(), paused ? app.pausedAt() : null, paused ? app.takedownActor() : null,
                advertiser ? app.takedownChannel() : null, app.pausedMinutes(), app.stoppedAt(), app.refundFrom(), proratableBase(app, items, ops.paidAmount(app)),
                app.stopFeeRate(), actorName);
    }

    /** 사업자 쪽 표시 이름(닉네임 — 없으면 상호) */
    public String nameOf(AppRow app) {
        String nick = store.nickname(app.userId());
        return nick != null ? nick : app.businessName();
    }

    /** 일할 대상 금액 — 결제액 × (대상 줄 공급가 / 모든 줄 공급가). 핫딜 글은 노출 자리 줄만(게시비 제외 — 노출 없으면 0) */
    static long proratableBase(AppRow app, List<ItemRow> items, long paid) {
        long total = items.stream().mapToLong(ItemRow::supply).sum();
        long eligible = app.kind() == BizApplicationKind.HOTDEAL_POST
                ? items.stream().filter(i -> i.product() != BizProduct.HOTDEAL_POST).mapToLong(ItemRow::supply).sum() : total;
        return BizPricing.base(paid, eligible, total);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private void pauseLocked(Who who, AppRow app) {
        Instant now = ops.now();
        requireKind(app);
        BizTransitions.require(Action.ADVERTISER_PAUSE, app.status());
        if (BizPhase.of(app.startAt(), app.endAt(), now) != BizPhase.RUNNING || refunds.lockOpen(app.id()).isPresent()) {
            throw BizApplicationException.stateChanged();
        }
        if (app.kind() == BizApplicationKind.HOTDEAL_POST && !dealPosts.dealPausable(app, false)) {
            throw BizApplicationException.stateChanged();
        }
        int limit = props.pauseLimit();
        if (app.pauseCount() >= limit) {
            throw BizApplicationException.pauseLimit();
        }
        if (!store.pause(app.id(), who.actor(), limit, now)) {
            throw BizApplicationException.stateChanged();
        }
        ops.event(app.id(), BizEventAction.PAUSED, who.type(), who.actorId(), note(who.tokens()), now);
        if (who.byOperator()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("channel", who.channel().name());
            detail.put("memo", who.memo());
            detail.put("pauseCount", app.pauseCount() + 1);
            ops.audit(who.operator().operatorId(), who.operator().ip(), AuditActions.BIZ_APP_PAUSE, app, detail);
        }
        ops.notices().member(app, who.byOperator() ? BizApplicationNotices.Kind.PAUSED_BY_OPERATOR : BizApplicationNotices.Kind.PAUSED, 0, null);
        ops.sellerHidden(app, PostEntity.SELLER_PAUSED);
    }

    private void resumeLocked(Who who, AppRow app) {
        Instant now = ops.now();
        BizTransitions.require(Action.ADVERTISER_RESUME, app.status());
        if (!app.paused() || !now.isBefore(app.endAt())) {
            throw BizApplicationException.stateChanged(); // 정지한 채 끝났으면 다시 켤 것이 없다(환불 없음)
        }
        if (!who.byOperator()) {
            eligibility.requireApproved(who.userId(), app.kind().service());
        }
        int minutes = minutes(app.pausedAt(), now);
        if (!store.resume(app.id(), minutes, now)) {
            throw BizApplicationException.stateChanged();
        }
        List<String> tokens = new ArrayList<>();
        tokens.add(MINUTES_PREFIX + minutes);
        tokens.addAll(who.tokens());
        ops.event(app.id(), BizEventAction.RESUMED, who.type(), who.actorId(), note(tokens), now);
        if (who.byOperator()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("channel", who.channel().name());
            detail.put("memo", who.memo());
            detail.put("pausedMinutes", minutes);
            ops.audit(who.operator().operatorId(), who.operator().ip(), AuditActions.BIZ_APP_RESUME, app, detail);
        }
        ops.notices().member(app, who.byOperator() ? BizApplicationNotices.Kind.RESUMED_BY_OPERATOR : BizApplicationNotices.Kind.RESUMED, 0, null);
        ops.sellerShown(app);
    }

    private void stopLocked(Who who, AppRow app, String reason) {
        Instant now = ops.now();
        requireKind(app);
        BizTransitions.require(Action.ADVERTISER_STOP, app.status());
        boolean fromPause = app.status() == BizApplicationStatus.TAKEN_DOWN;
        if (fromPause ? !app.paused() || !now.isBefore(app.endAt()) : BizPhase.of(app.startAt(), app.endAt(), now) != BizPhase.RUNNING) {
            throw BizApplicationException.stateChanged();
        }
        if (refunds.lockOpen(app.id()).isPresent()) {
            throw BizApplicationException.stateChanged();
        }
        if (app.kind() == BizApplicationKind.HOTDEAL_POST && !dealPosts.dealPausable(app, fromPause)) {
            throw BizApplicationException.stateChanged();
        }
        Instant refundFrom = fromPause ? app.pausedAt() : now;
        int addMinutes = fromPause ? minutes(app.pausedAt(), now) : 0;
        String text = BizApplicationOps.cut(reason, 200);
        if (!store.stop(app.id(), app.status(), who.actor(), text, refundFrom, addMinutes, now)) {
            throw BizApplicationException.stateChanged();
        }
        List<String> tokens = new ArrayList<>();
        if (fromPause) {
            tokens.add(FROM_PAUSE);
        }
        tokens.addAll(who.tokens());
        ops.event(app.id(), BizEventAction.STOPPED, who.type(), who.actorId(), note(tokens), now);
        // 일할 환불 — 대상 금액(핫딜 글은 노출 자리 줄만) · 기준 시각 · 이미 환불한 몫 · 그 건에 고정된 수수료율
        long paid = ops.paidAmount(app);
        long base = proratableBase(app, store.items(app.id()), paid);
        BizPricing.Stop calc = BizPricing.stop(paid, base, app.startAt(), app.endAt(), refundFrom, refunds.approvedTotal(app.id()), app.stopFeeRate());
        if (calc.proposed() > 0) {
            try {
                refunds.insertStop(new NewStopRefund(ops.nextId(), app.id(), app.paymentMethod(), paid, calc.prorated(), calc.feeRate(), calc.fee(), calc.usedDays(),
                        calc.totalDays(), refundFrom, BizApplicationOps.cut(reason, 300), now));
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw BizApplicationException.stateChanged();
            }
            ops.event(app.id(), BizEventAction.REFUND_REQUESTED, who.type(), who.actorId(), STOP_NOTE, now);
            ops.notices().refundRequested(app);
        }
        if (who.byOperator()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("channel", who.channel().name());
            detail.put("memo", who.memo());
            detail.put("fromPause", fromPause);
            detail.put("refundFrom", refundFrom.toString());
            detail.put("prorated", calc.prorated());
            detail.put("feeRate", calc.feeRate());
            detail.put("fee", calc.fee());
            detail.put("proposed", calc.proposed());
            ops.audit(who.operator().operatorId(), who.operator().ip(), AuditActions.BIZ_APP_STOP, app, detail);
        }
        ops.notices().member(app, who.byOperator() ? BizApplicationNotices.Kind.STOPPED_BY_OPERATOR : BizApplicationNotices.Kind.STOPPED, calc.proposed(), null);
        ops.sellerHidden(app, PostEntity.SELLER_STOPPED);
    }

    private static void requireKind(AppRow app) {
        if (!KINDS.contains(app.kind())) {
            throw BizApplicationException.stateChanged();
        }
    }

    private static Who member(long userId) {
        return new Who(BizActorType.MEMBER, userId, null, null, null);
    }

    private static Who operator(BizOperator actor, String channelText, String memoText) {
        BizFields f = BizFields.start();
        BizPauseChannel channel = f.oneOf("channel", channelText, BizPauseChannel::valueOf);
        String memo = f.text("memo", memoText, 200);
        f.throwIfAny();
        return new Who(BizActorType.OPERATOR, null, actor, channel, memo);
    }

    /** 멈춘 분 — 분 단위 버림, 음수 없음 */
    static int minutes(Instant from, Instant to) {
        if (from == null || !to.isAfter(from)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Duration.between(from, to).toMinutes());
    }

    /** 토큰을 ` · ` 로 잇는다(없으면 null) */
    static String note(List<String> tokens) {
        return tokens.isEmpty() ? null : String.join(" · ", tokens);
    }
}

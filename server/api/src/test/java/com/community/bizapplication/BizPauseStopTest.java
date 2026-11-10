package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.ad.dto.AdPlacements;
import com.community.ad.dto.PublicAdView;
import com.community.ad.repository.master.AdPlacementStore;
import com.community.ad.service.PublicAdService;
import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizPauseService;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizPricing;
import com.community.business.dto.BusinessSuspendPlan;
import com.community.home.service.GadgetService;
import com.community.hotdeal.dto.response.DealStoreChipView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.dto.response.PostListResponse;
import com.community.post.dto.response.PostRowView;
import com.community.post.exception.DealSellerHiddenException;
import com.community.post.service.PostQueryService;
import com.community.profile.service.UserProfileService;
import com.community.search.dto.response.SearchRowView;
import com.community.search.service.SearchService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * 광고·사업자 핫딜 일시 정지 / 중단 + 중도 해지 수수료(docs/features/biz-pause-contract.md 규칙 1~11 — commerce.md 18절, 돈 규칙이라 모두 시험으로 막는다).
 *
 * <ul>
 *   <li>규칙 1~3: 정지 3회(4번째 409 {@code BIZ_PAUSE_LIMIT} · 운영자 대신 처리도 같은 셈) · 다시 켜기(끝 전만 · 끝 시각 그대로 · 멈춘 분 누적) · 정지 중 공개 노출·집계 없음.</li>
 *   <li>규칙 4~8: 중단 환불액 = 일할(대상 금액 · 기준 시각 · 이미 환불한 몫 빼기) − 수수료(신청 때 고정된 비율) · 핫딜 글은 게시비 제외 · 노출 없는 글 0원(환불 줄 없음) ·
 *       수수료 없는 것(시작 전 취소 · DOWNTIME · 사업자 정지 훅) · 진행 중 refund-request = 중단 · 환불 규정 버전 · 옛 버전 0%.</li>
 *   <li>규칙 9·11: 핫딜 글 판매자 숨김 · 직접 진입 403 {@code DEAL_SELLER_HIDDEN} · 다시 켜면 글·노출 복귀 · 운영자 대신 처리 경로·메모 검증 · 환불 승인 → CANCELED(까닭 남음)·거절 → 내림 그대로 ·
 *       광고주 요청 건 되돌리기 409.</li>
 * </ul>
 */
class BizPauseStopTest extends BizHotdealTestBase {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0";

    @Autowired
    private BizPauseService pauses;
    @Autowired
    private PublicAdService publicAds;
    @Autowired
    private PostQueryService postQueries;
    @Autowired
    private AdPlacementStore placementStore;
    @Autowired
    private com.community.bizapplication.repository.master.BizDealPostStore dealPostStore;
    @Autowired
    private SearchService searches;
    @Autowired
    private GadgetService gadgets;
    @Autowired
    private UserProfileService profiles;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @AfterEach
    void resetSettings() {
        jdbc.update("DELETE FROM ad_placement_settings");
        publicAds.invalidate();
    }

    private record Running(long user, long id, BizPeriods.Period period) {
    }

    /** 결제한 한 줄 광고(온라인, {@code days} 일) — 시계는 아직 시작 전 */
    private Running paidText(int days) {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, days, "ONLINE");
        BizPeriods.Period p = freshPeriod(days);
        approve(id, "ONLINE", p);
        pay(user, id);
        return new Running(user, id, p);
    }

    /** 결제한 한 줄 광고 + 시계를 시작 한 시간 뒤로 */
    private Running runningText(int days) {
        Running r = paidText(days);
        clock.set(r.period().start().plus(Duration.ofHours(1)));
        return r;
    }

    private void at(Running r, Duration fromStart) {
        clock.set(r.period().start().plus(fromStart));
    }

    private RefundRow openRefund(long id) {
        return tx.execute(s -> refunds.lockOpen(id)).orElseThrow();
    }

    private static long fee(long prorated, int rate) {
        return Math.floorDiv(prorated * rate, 100);
    }

    private List<String> live(String types) {
        publicAds.invalidate();
        return publicAds.list(types, null, null).items().stream().map(PublicAdView.Ad::id).toList();
    }

    private int impress(long id) {
        return publicAds.impressions(new PublicAdService.Visitor(null, "203.0.113.77", UA),
                new PublicAdView.Impressions(List.of(new PublicAdView.Impression(Long.toString(id), 1, "HOME"))));
    }

    // ── 규칙 1·3 정지 횟수 · 다시 켜기 ───────────────────────────────────────

    @Test
    void 규칙1_정지는_한_건에_3번이고_4번째는_409_BIZ_PAUSE_LIMIT_운영자_대신_처리도_같은_셈() {
        Running r = runningText(7);
        pauses.pause(r.user(), r.id());
        pauses.resume(r.user(), r.id());
        pauses.pauseByOperator(operator, r.id(), "PHONE", "전화 요청");
        pauses.resumeByOperator(operator, r.id(), "PHONE", "전화 요청");
        pauses.pause(r.user(), r.id());
        pauses.resume(r.user(), r.id());
        assertThat(app(r.id()).pauseCount()).isEqualTo(3);
        assertThat(codeOf(() -> pauses.pause(r.user(), r.id()))).isEqualTo("BIZ_PAUSE_LIMIT");
        assertThat(codeOf(() -> pauses.pauseByOperator(operator, r.id(), "EMAIL", "메일 요청"))).isEqualTo("BIZ_PAUSE_LIMIT");
        assertThat(app(r.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        BizMemberView.Pause pause = view.detail(r.user(), r.id()).pause();
        assertThat(pause.used()).isEqualTo(3);
        assertThat(pause.limit()).isEqualTo(3);
        // 한도를 다 써도 중단은 된다
        pauses.stop(r.user(), r.id(), "그만할게요");
        assertThat(app(r.id()).takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_STOP);
    }

    @Test
    void 규칙3_다시_켜기는_승인_없이_바로_예약으로_끝_시각을_늘리지_않고_멈춘_분을_더한다() {
        Running r = runningText(7);
        Instant end = app(r.id()).endAt();
        pauses.pause(r.user(), r.id());
        AppRow paused = app(r.id());
        assertThat(paused.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(paused.takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_PAUSE);
        assertThat(paused.takedownActor()).isEqualTo(BizActorType.MEMBER);
        assertThat(paused.pausedAt()).isEqualTo(clock.instant());
        BizMemberView.Detail d = view.detail(r.user(), r.id());
        assertThat(d.takedownCause()).isEqualTo("ADVERTISER_PAUSE");
        assertThat(d.pause().pausedAt()).isEqualTo(paused.pausedAt());
        assertThat(d.pause().pausedBy()).isEqualTo("ME");
        assertThat(d.pause().feeRate()).isEqualTo(paused.stopFeeRate());
        assertThat(d.pause().proratableTotal()).isEqualTo(paused.approved().total());
        assertThat(view.list(r.user(), "ALL").items().stream().filter(s -> s.id().equals(Long.toString(r.id()))).findFirst().orElseThrow().takedownCause())
                .isEqualTo("ADVERTISER_PAUSE");

        clock.set(clock.instant().plus(Duration.ofMinutes(90)).plusSeconds(20));
        pauses.resume(r.user(), r.id());
        AppRow back = app(r.id());
        assertThat(back.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(back.takedownCause()).isNull();
        assertThat(back.pausedAt()).isNull();
        assertThat(back.endAt()).isEqualTo(end);
        assertThat(store.items(r.id())).allSatisfy(i -> assertThat(i.endAt()).isEqualTo(end));
        assertThat(back.pausedMinutes()).isEqualTo(90);
        assertThat(eventNote(r.id(), "RESUMED")).isEqualTo("MIN:90");
        assertThat(memberNotices(r.user(), "PAUSED")).isOne();
        assertThat(memberNotices(r.user(), "RESUMED")).isOne();
        // 정지 중이 아니면 다시 켤 것이 없다
        assertThat(codeOf(() -> pauses.resume(r.user(), r.id()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 정지는_진행_중만_다시_켜기와_중단은_끝_뒤_409_정지한_채_끝나면_상태_그대로() {
        Running r = paidText(3);
        assertThat(codeOf(() -> pauses.pause(r.user(), r.id()))).as("시작 전").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        at(r, Duration.ofHours(2));
        pauses.pause(r.user(), r.id());
        clock.set(r.period().end());
        assertThat(codeOf(() -> pauses.resume(r.user(), r.id()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> pauses.stop(r.user(), r.id(), "끝났지만"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> members.requestRefund(r.user(), r.id(), "끝났지만"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        AppRow a = app(r.id());
        assertThat(a.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(a.takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_PAUSE);
        assertThat(refundRows(r.id())).isZero();
        Running ended = paidText(2);
        clock.set(ended.period().end().plusSeconds(60));
        assertThat(codeOf(() -> pauses.pause(ended.user(), ended.id()))).as("끝난 뒤").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        // 남의 건은 404
        assertThat(codeOf(() -> pauses.pause(approvedBusiness(), r.id()))).isEqualTo("BIZ_APPLICATION_NOT_FOUND");
    }

    // ── 규칙 2 정지 중 공개 노출 ─────────────────────────────────────────────

    @Test
    void 규칙2_정지_중에는_공개_노출에서_빠지고_집계도_0_다시_켜면_다시_나간다() {
        Running r = runningText(3);
        String id = Long.toString(r.id());
        assertThat(live("TEXT_LINE")).contains(id);
        pauses.pause(r.user(), r.id());
        assertThat(live("TEXT_LINE")).doesNotContain(id);
        assertThat(impress(r.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_daily_stats WHERE application_id = ?", Integer.class, r.id())).isZero();
        // 자리는 계속 그 광고주 몫(내림이 자리를 차지한다)
        assertThat(BizApplicationStatus.TAKEN_DOWN.occupying()).isTrue();
        pauses.resume(r.user(), r.id());
        assertThat(live("TEXT_LINE")).contains(id);
        assertThat(impress(r.id())).isOne();
    }

    // ── 규칙 4·5 중단 환불액 ────────────────────────────────────────────────

    @Test
    void 규칙5_진행_중_중단은_중단_시각_기준_일할에서_수수료를_뺀_예상액으로_심사_대기() {
        Running r = paidText(7);
        at(r, Duration.ofDays(2).plusHours(3)); // 7일 중 3일째(그날 포함 쓴 날 3)
        pauses.stop(r.user(), r.id(), "행사 종료");
        AppRow a = app(r.id());
        assertThat(a.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(a.takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_STOP);
        assertThat(a.stoppedAt()).isEqualTo(clock.instant());
        assertThat(a.refundFrom()).isEqualTo(clock.instant());
        long total = a.approved().total();
        long prorated = Math.floorDiv(total * 4, 7);
        RefundRow f = openRefund(r.id());
        assertThat(f.type()).isEqualTo(BizRefundType.STOP_PRORATED);
        assertThat(f.status()).isEqualTo(BizRefundStatus.REQUESTED);
        assertThat(f.usedDays()).isEqualTo(3);
        assertThat(f.totalDays()).isEqualTo(7);
        assertThat(f.paidAmount()).isEqualTo(total);
        assertThat(f.proratedAmount()).isEqualTo(prorated);
        assertThat(f.feeRate()).isEqualTo(a.stopFeeRate()).isEqualTo(10);
        assertThat(f.feeAmount()).isEqualTo(fee(prorated, 10));
        assertThat(f.proposedAmount()).isEqualTo(prorated - fee(prorated, 10));
        assertThat(f.basisAt()).isEqualTo(a.refundFrom());
        assertThat(f.reason()).isEqualTo("행사 종료");
        assertThat(events(r.id())).contains("STOPPED", "REFUND_REQUESTED");
        assertThat(eventNote(r.id(), "STOPPED")).isNull();
        assertThat(eventNote(r.id(), "REFUND_REQUESTED")).isEqualTo("ADVERTISER_STOP");
        assertThat(memberNotices(r.user(), "STOPPED")).isOne();
        BizMemberView.Refund pub = view.detail(r.user(), r.id()).refund();
        assertThat(pub.type()).isEqualTo("STOP_PRORATED");
        assertThat(pub.proposedAmount()).isEqualTo(f.proposedAmount());
        assertThat(pub.proratedAmount()).isEqualTo(prorated);
        assertThat(pub.feeAmount()).isEqualTo(f.feeAmount());
        // 되돌리기 없음 · 다시 중단·정지 409
        assertThat(codeOf(() -> pauses.stop(r.user(), r.id(), "또"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> pauses.resume(r.user(), r.id()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(fieldsOf(() -> pauses.stop(r.user(), r.id(), " "))).containsExactly("reason");
    }

    @Test
    void 규칙4_정지_중에_중단하면_멈춘_시각이_일할_기준이고_멈춘_분이_더해진다() {
        Running r = paidText(7);
        at(r, Duration.ofDays(1).plusHours(2)); // 2일째에 정지
        pauses.pause(r.user(), r.id());
        Instant pausedAt = clock.instant();
        at(r, Duration.ofDays(4).plusHours(1)); // 5일째에 중단
        pauses.stop(r.user(), r.id(), "정지하다 중단");
        AppRow a = app(r.id());
        assertThat(a.refundFrom()).isEqualTo(pausedAt);
        assertThat(a.pausedAt()).isNull();
        assertThat(a.pausedMinutes()).isEqualTo((int) Duration.between(pausedAt, clock.instant()).toMinutes());
        RefundRow f = openRefund(r.id());
        assertThat(f.usedDays()).isEqualTo(2);
        long prorated = Math.floorDiv(a.approved().total() * 5, 7);
        assertThat(f.proratedAmount()).isEqualTo(prorated);
        assertThat(f.proposedAmount()).isEqualTo(prorated - fee(prorated, a.stopFeeRate()));
        assertThat(f.basisAt()).isEqualTo(pausedAt);
        assertThat(eventNote(r.id(), "STOPPED")).isEqualTo("FROM_PAUSE");
        assertThat(view.detail(r.user(), r.id()).pause().refundFrom()).isEqualTo(pausedAt);
    }

    @Test
    void 규칙5_이미_돌려준_DOWNTIME_은_빼고_DOWNTIME_자체에는_수수료가_없다() {
        Running r = paidText(7);
        long paid = app(r.id()).approved().total();
        at(r, Duration.ofHours(1));
        ads.takedown(operator, r.id(), "점검", false);
        at(r, Duration.ofHours(2));
        ads.restore(operator, r.id(), "PRORATE_REFUND", "운영 귀책"); // 1일치
        RefundRow downtime = refunds.latest(r.id()).orElseThrow();
        assertThat(downtime.type()).isEqualTo(BizRefundType.DOWNTIME);
        assertThat(downtime.feeRate()).isNull();
        assertThat(downtime.feeAmount()).isNull();
        assertThat(downtime.approvedAmount()).isEqualTo(Math.floorDiv(paid, 7));
        at(r, Duration.ofDays(3).plusHours(1)); // 4일째 — 남은 3일
        pauses.stop(r.user(), r.id(), "중단");
        long prorated = Math.min(Math.floorDiv(paid * 3, 7), paid - downtime.approvedAmount());
        RefundRow f = openRefund(r.id());
        assertThat(f.proratedAmount()).isEqualTo(prorated);
        assertThat(f.proposedAmount()).isEqualTo(prorated - fee(prorated, 10));
        // 승인 한도 = 결제액 − 이미 환불
        assertThat(fieldsOf(() -> review.approveRefund(operator, r.id(), paid - downtime.approvedAmount() + 1, "초과"))).containsExactly("amount");
    }

    @Test
    void 규칙5_원_미만은_버린다() {
        // 대상 금액 비례·일할·수수료 모두 버림
        assertThat(BizPricing.base(10_001, 1, 3)).isEqualTo(3_333);
        assertThat(BizPricing.base(10_000, 0, 3)).isZero();
        assertThat(BizPricing.base(10_000, 3, 3)).isEqualTo(10_000);
        BizPeriods.Period p = BizPeriods.ofDates(java.time.LocalDate.of(2031, 3, 1), java.time.LocalDate.of(2031, 3, 7));
        BizPricing.Stop s = BizPricing.stop(10_001, 10_001, p.start(), p.end(), p.start().plus(Duration.ofDays(2)), 0, 10);
        assertThat(s.usedDays()).isEqualTo(3);
        assertThat(s.prorated()).isEqualTo(Math.floorDiv(10_001L * 4, 7)); // 5714
        assertThat(s.fee()).isEqualTo(571);
        assertThat(s.proposed()).isEqualTo(5_714 - 571);
        assertThat(BizPricing.stop(10_001, 10_001, p.start(), p.end(), p.start().plus(Duration.ofDays(2)), 9_000, 10).prorated()).isEqualTo(1_001);
    }

    // ── 규칙 7·8 수수료율 고정 · 환불 규정 버전 ──────────────────────────────

    @Test
    void 규칙7_수수료율은_신청_때_고정되고_뒤에_설정을_바꿔도_그_건은_그대로() {
        tx.executeWithoutResult(s -> placementStore.save(AdPlacements.DEFAULTS, 20, operator.operatorId(), clock.instant()));
        assertThat(members.refundPolicy().stopFeeRate()).isEqualTo(20);
        assertThat(view.refundPolicy().version()).isEqualTo("2026-11-02");
        Running r = paidText(4);
        assertThat(app(r.id()).stopFeeRate()).isEqualTo(20);
        tx.executeWithoutResult(s -> placementStore.save(AdPlacements.DEFAULTS, 5, operator.operatorId(), clock.instant()));
        assertThat(view.refundPolicy().stopFeeRate()).isEqualTo(5);
        assertThat(app(r.id()).stopFeeRate()).isEqualTo(20);
        at(r, Duration.ofHours(3));
        pauses.stop(r.user(), r.id(), "중단");
        RefundRow f = openRefund(r.id());
        assertThat(f.feeRate()).isEqualTo(20);
        assertThat(f.feeAmount()).isEqualTo(fee(f.proratedAmount(), 20));
        // 운영자 등록 건도 등록 때 비율을 고정한다
        BizPeriods.Period op = freshPeriod(2);
        long ad = ads.createManual(operator, new com.community.ad.service.AdOperatorService.Manual("biz-" + profileOf(approvedBusiness()), "TEXT_LINE",
                op.start().toString(), op.end().toString(), 5, new com.community.ad.service.AdOperatorService.Creative(null, "운영자 등록 한 줄",
                "https://a.example.com/op", null), 10_000L, List.of(), null));
        assertThat(app(ad).stopFeeRate()).isEqualTo(5);
    }

    @Test
    void 규칙8_옛_환불_규정_버전_신청은_400_이고_옛_버전에_동의한_건은_수수료_0퍼센트() {
        long user = approvedBusiness();
        java.time.LocalDate start = today().plusDays(1);
        assertThat(fieldsOf(() -> members.submitAd(user, new com.community.bizapplication.dto.request.BizAdApplyRequest("TEXT_LINE", start.toString(), start.toString(),
                null, "https://a.example.com/x", null, "한 줄", "ONLINE", POLICY, "2026-10-30")))).containsExactly("agreedRefundPolicyVersion");
        // 새 줄은 수수료율을 반드시 넣는다(기본값 없음 — 이미 있던 줄만 마이그레이션이 0 으로 채웠다)
        assertThat(jdbc.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_name = 'biz_applications' AND column_name = 'stop_fee_rate'",
                String.class)).isNull();
        Running r = paidText(4);
        jdbc.update("UPDATE biz_applications SET stop_fee_rate = 0, refund_policy_version = '2026-10-30' WHERE id = ?", r.id()); // V127 이전에 들어온 건
        at(r, Duration.ofHours(3));
        pauses.stop(r.user(), r.id(), "중단");
        RefundRow f = openRefund(r.id());
        assertThat(f.feeRate()).isZero();
        assertThat(f.feeAmount()).isZero();
        assertThat(f.proposedAmount()).isEqualTo(f.proratedAmount()).isEqualTo(Math.floorDiv(app(r.id()).approved().total() * 3, 4));
    }

    // ── 규칙 6 수수료 없는 것 · 진행 중 refund-request ─────────────────────────

    @Test
    void 규칙6_시작_전_취소는_전액이고_수수료가_없다() {
        Running r = paidText(3);
        members.requestRefund(r.user(), r.id(), "취소");
        RefundRow f = refunds.latest(r.id()).orElseThrow();
        assertThat(app(r.id()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(f.type()).isEqualTo(BizRefundType.FULL_AUTO);
        assertThat(f.approvedAmount()).isEqualTo(app(r.id()).approved().total());
        assertThat(f.feeRate()).isNull();
        assertThat(f.proratedAmount()).isNull();
    }

    @Test
    void 규칙6_사업자_정지_훅의_일할에는_수수료가_없다() {
        Running r = paidText(7);
        at(r, Duration.ofDays(2).plusHours(3));
        tx.executeWithoutResult(s -> hook.onSuspended(profileOf(r.user()), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD,
                BusinessSuspendPlan.Running.TAKEDOWN_PRORATE), operator.operatorId()));
        RefundRow f = openRefund(r.id());
        assertThat(f.type()).isEqualTo(BizRefundType.SUSPEND_PRORATED);
        assertThat(f.feeRate()).isNull();
        assertThat(f.proposedAmount()).isEqualTo(Math.floorDiv(app(r.id()).approved().total() * 4, 7));
    }

    @Test
    void 진행_중_refund_request_는_중단과_같고_정지_중이면_정지_중_전환이다() {
        Running r = paidText(5);
        at(r, Duration.ofHours(5));
        members.requestRefund(r.user(), r.id(), "중도 해지");
        assertThat(app(r.id()).takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_STOP);
        assertThat(openRefund(r.id()).type()).isEqualTo(BizRefundType.STOP_PRORATED);
        assertThat(store.countEvents(r.id(), com.community.bizapplication.entity.BizEventAction.REFUND_REQUESTED)).isOne();

        Running q = paidText(5);
        at(q, Duration.ofHours(1));
        pauses.pause(q.user(), q.id());
        Instant pausedAt = clock.instant();
        at(q, Duration.ofDays(1));
        members.requestRefund(q.user(), q.id(), "정지 중 해지");
        assertThat(app(q.id()).refundFrom()).isEqualTo(pausedAt);
        assertThat(eventNote(q.id(), "STOPPED")).isEqualTo("FROM_PAUSE");
    }

    // ── 환불 결정 · 되돌리기 ────────────────────────────────────────────────

    @Test
    void 중단_환불_승인은_CANCELED_로_닫고_까닭을_남기고_거절이면_중단_내림_그대로() {
        Running r = paidText(4);
        at(r, Duration.ofHours(3));
        pauses.stop(r.user(), r.id(), "중단");
        RefundRow f = openRefund(r.id());
        review.approveRefund(operator, r.id(), f.proposedAmount(), "제안대로");
        AppRow a = app(r.id());
        assertThat(a.status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(a.takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_STOP);
        assertThat(refunds.latest(r.id()).orElseThrow().approvedAmount()).isEqualTo(f.proposedAmount());
        assertThat(view.detail(r.user(), r.id()).pause()).isNotNull();

        Running q = paidText(4);
        at(q, Duration.ofHours(3));
        pauses.stop(q.user(), q.id(), "중단");
        review.denyRefund(operator, q.id(), "규정상 불가");
        AppRow d = app(q.id());
        assertThat(d.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(d.takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_STOP);
        assertThat(codeOf(() -> members.requestRefund(q.user(), q.id(), "다시"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> pauses.stop(q.user(), q.id(), "다시"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 광고주_요청_정지_중단_건의_관리자_되돌리기는_409() {
        Running r = runningText(4);
        pauses.pause(r.user(), r.id());
        assertThat(codeOf(() -> ads.restore(operator, r.id(), "EXTEND", "되돌리기"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> ads.restore(operator, r.id(), "PRORATE_REFUND", "되돌리기"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        pauses.stop(r.user(), r.id(), "중단");
        assertThat(codeOf(() -> ads.restore(operator, r.id(), "EXTEND", "되돌리기"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(app(r.id()).takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_STOP);
    }

    @Test
    void 열린_환불이_있으면_정지_중단_409() {
        Running r = paidText(7);
        at(r, Duration.ofDays(2));
        tx.executeWithoutResult(s -> hook.onSuspended(profileOf(r.user()), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD,
                BusinessSuspendPlan.Running.TAKEDOWN_PRORATE), operator.operatorId()));
        jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED', taken_down_at = NULL, takedown_cause = NULL WHERE id = ?", r.id()); // 열린 환불만 남긴다
        assertThat(codeOf(() -> pauses.pauseByOperator(operator, r.id(), "PHONE", "요청"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> pauses.stopByOperator(operator, r.id(), "PHONE", "요청", null))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    // ── 규칙 11 운영자 대신 처리 ────────────────────────────────────────────

    @Test
    void 규칙11_운영자_대신_처리는_경로와_메모_필수_공개_처리_기록엔_경로만() {
        Running r = runningText(5);
        assertThat(fieldsOf(() -> pauses.pauseByOperator(operator, r.id(), null, "메모"))).containsExactly("channel");
        assertThat(fieldsOf(() -> pauses.pauseByOperator(operator, r.id(), "FAX", "메모"))).containsExactly("channel");
        assertThat(fieldsOf(() -> pauses.pauseByOperator(operator, r.id(), "PHONE", " "))).containsExactly("memo");
        assertThat(fieldsOf(() -> pauses.pauseByOperator(operator, r.id(), "PHONE", "가".repeat(201)))).containsExactly("memo");
        assertThat(fieldsOf(() -> pauses.resumeByOperator(operator, r.id(), "OTHER", null))).containsExactly("memo");
        assertThat(fieldsOf(() -> pauses.stopByOperator(operator, r.id(), null, null, null))).containsExactlyInAnyOrder("channel", "memo");
        assertThat(app(r.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);

        pauses.pauseByOperator(operator, r.id(), "PHONE", "광고주 전화 · 내부 메모");
        AppRow a = app(r.id());
        assertThat(a.takedownActor()).isEqualTo(BizActorType.OPERATOR);
        assertThat(a.takenDownBy()).isEqualTo(operator.operatorId());
        assertThat(a.takedownMemo()).isEqualTo("광고주 전화 · 내부 메모");
        assertThat(eventNote(r.id(), "PAUSED")).isEqualTo("PHONE · 광고주 전화 · 내부 메모");
        assertThat(auditCount("BIZ_APPLICATION", Long.toString(r.id()), "BIZ_APP_PAUSE")).isOne();
        assertThat(memberNotices(r.user(), "PAUSED_BY_OPERATOR")).isOne();
        BizMemberView.Detail d = view.detail(r.user(), r.id());
        assertThat(d.history()).filteredOn(h -> h.action().equals("PAUSED")).singleElement().satisfies(h -> {
            assertThat(h.note()).isEqualTo("PHONE");
            assertThat(h.actor()).isEqualTo("OPERATOR");
        });
        assertThat(d.pause().pausedBy()).isEqualTo("OPERATOR");
        assertThat(d.pause().channel()).isEqualTo("PHONE");
        assertThat(pauses.info(a, store.items(r.id())).actorName()).isNotNull();

        clock.set(clock.instant().plus(Duration.ofMinutes(10)));
        pauses.resumeByOperator(operator, r.id(), "EMAIL", "메일로 재개 요청");
        assertThat(eventNote(r.id(), "RESUMED")).isEqualTo("MIN:10 · EMAIL · 메일로 재개 요청");
        assertThat(view.detail(r.user(), r.id()).history()).filteredOn(h -> h.action().equals("RESUMED")).singleElement()
                .satisfies(h -> assertThat(h.note()).isEqualTo("MIN:10 · EMAIL"));
        assertThat(auditCount("BIZ_APPLICATION", Long.toString(r.id()), "BIZ_APP_RESUME")).isOne();

        pauses.stopByOperator(operator, r.id(), "OTHER", "방문 요청", null);
        AppRow s = app(r.id());
        assertThat(s.takedownText()).as("메모는 사업자 화면의 사유로 옮기지 않는다").isNull();
        assertThat(openRefund(r.id()).reason()).isNull();
        assertThat(eventNote(r.id(), "STOPPED")).isEqualTo("OTHER · 방문 요청");
        assertThat(auditLogs.where().target(Long.toString(r.id())).action("BIZ_APP_STOP").single().retentionClass())
                .isEqualTo("MONEY");
        Map<String, Object> audit = lastAudit("BIZ_APPLICATION", Long.toString(r.id()), "BIZ_APP_STOP");
        assertThat(audit.get("detail").toString()).contains("feeRate", "proposed", "OTHER");
        assertThat(memberNotices(r.user(), "STOPPED_BY_OPERATOR")).isOne();
    }

    // ── 규칙 9 사업자 핫딜 글 ───────────────────────────────────────────────

    @Test
    void 규칙9_핫딜_글을_정지하면_글이_숨고_직접_진입은_403_DEAL_SELLER_HIDDEN_다시_켜면_글과_노출이_돌아온다() {
        Booked b = postedBooking("HOME_TOP");
        long postId = postOf(b.id());
        String id = Long.toString(b.id());
        assertThat(live("HOTDEAL_TOP")).contains(id);
        // 작성자에게만 신청 건 ID
        assertThat(postQueries.detail(Long.toString(postId), b.user(), "127.0.0.1").deal().business().applicationId()).isEqualTo(id);
        assertThat(postQueries.detail(Long.toString(postId), null, "127.0.0.1").deal().business().applicationId()).isNull();
        int views = jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId);

        pauses.pause(b.user(), b.id());
        assertThat(postRow(postId).get("hidden_reason")).isEqualTo("SELLER_PAUSED");
        assertThat(result(b.id()).get("hidden_at")).isNotNull();
        assertThat(eventNote(b.id(), "POST_HIDDEN")).isEqualTo("ADVERTISER_PAUSE");
        // 관리자 핫딜 글 줄의 sellerHidden(까닭 · 시각 · 누가)
        var hiddenRow = dealPostStore.sellerHidden(List.of(postId)).get(postId);
        assertThat(hiddenRow.cause()).isEqualTo("ADVERTISER_PAUSE");
        assertThat(hiddenRow.byOperator()).isFalse();
        assertThat(hiddenRow.by()).isNotBlank();
        assertThat(hiddenRow.at()).isEqualTo(app(b.id()).pausedAt());
        assertThat(live("HOTDEAL_TOP")).doesNotContain(id);
        assertThat(view.detail(b.user(), b.id()).post().placement().stopReason()).isEqualTo("PAUSED");
        assertThatThrownBy(() -> postQueries.detail(Long.toString(postId), null, "198.51.100.3")).isInstanceOfSatisfying(DealSellerHiddenException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(403);
            assertThat(e.getErrorCode()).isEqualTo("DEAL_SELLER_HIDDEN");
            assertThat(e.getExtras()).containsEntry("state", "PAUSED").containsEntry("boardSlug", boardSlug(dealBoard())).containsKeys("boardName", "boardNameEn")
                    .doesNotContainKey("applicationId");
        });
        assertThatThrownBy(() -> postQueries.detail(Long.toString(postId), b.user(), "198.51.100.3")).isInstanceOfSatisfying(DealSellerHiddenException.class,
                e -> assertThat(e.getExtras()).containsEntry("applicationId", id).doesNotContainKeys("body", "deal"));
        assertThat(jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId)).as("조회수 그대로").isEqualTo(views);

        pauses.resume(b.user(), b.id());
        assertThat(postRow(postId).get("hidden_at")).isNull();
        assertThat(result(b.id()).get("hidden_at")).isNull();
        assertThat(eventNote(b.id(), "POST_RESTORED")).isEqualTo("ADVERTISER_PAUSE");
        assertThat(live("HOTDEAL_TOP")).contains(id);
        assertThat(postQueries.detail(Long.toString(postId), null, "127.0.0.1").deal()).isNotNull();
        assertThat(view.detail(b.user(), b.id()).post().placement().status()).isEqualTo("SHOWING");
    }

    @Test
    void 규칙9_판매자_숨김_글은_목록_검색_가젯_쇼핑몰_칩_작성자_글에서_아예_빠지고_신고_숨김_글은_숨김_줄로_남는다() {
        // 같은 게시판의 운영 숨김 글 — 기존처럼 「숨겨진 글」 줄로 남아야 한다(먼저 만든다 — 게시하면 시계가 그 건의 기간으로 옮겨 간다)
        Booked staff = postedBooking("NONE");
        long staffId = postOf(staff.id());
        boardFixture.staffHide(staffId);
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        String pid = Long.toString(postId);
        String slug = boardSlug(dealBoard());
        String title = (String) postRow(postId).get("title");
        String store = (String) dealRow(postId).get("store_name");

        PostListResponse before = postQueries.list(slug, null, null, null, 1, null, null, null);
        assertThat(rowIds(before)).contains(pid, Long.toString(staffId));
        long chipBefore = chip(before, store);

        for (Runnable hide : List.<Runnable>of(() -> pauses.pause(b.user(), b.id()), () -> pauses.stop(b.user(), b.id(), "판매 종료"))) {
            hide.run();
            PostListResponse list = postQueries.list(slug, null, null, null, 1, null, null, null);
            assertThat(rowIds(list)).doesNotContain(pid).contains(Long.toString(staffId));
            assertThat(list.items()).filteredOn(r -> r.id().equals(Long.toString(staffId))).singleElement().satisfies(r -> assertThat(r.title()).isEmpty());
            assertThat(list.totalCount()).as("전체 수에서도 빠진다").isEqualTo(before.totalCount() - 1);
            assertThat(chip(list, store)).as("쇼핑몰 칩 수").isEqualTo(chipBefore - 1);
            assertThat(rowIds(postQueries.list(slug, null, "POPULAR", null, 1, null, false, null))).as("종료 보이기·인기순").doesNotContain(pid);
            assertThat(rowIds(postQueries.list(slug, null, null, null, 1, null, null, store))).as("쇼핑몰 거르기").doesNotContain(pid);
            assertThat(rowIds(postQueries.list(slug, null, null, title, 1, null, null, null))).as("게시판 안 검색").doesNotContain(pid);
            assertThat(searches.search(title, null, null, null, null, null, null, null).items()).as("통합 검색").extracting(SearchRowView::id).doesNotContain(pid);
            assertThat(gadgets.hotDeals(JSON.createObjectNode().put("sort", "LATEST").put("count", 10), null).items()).as("가젯 HOT_DEALS")
                    .extracting(PostFeedRowView::id).doesNotContain(pid);
            assertThat(profiles.posts(Long.toString(b.user()), null, null, null).items()).as("작성자 글 목록").extracting(PostFeedRowView::id).doesNotContain(pid);
        }
    }

    private static List<String> rowIds(PostListResponse list) {
        return java.util.stream.Stream.concat(list.pinned().stream(), list.items().stream()).map(PostRowView::id).toList();
    }

    private static long chip(PostListResponse list, String store) {
        return list.stores() == null ? 0 : list.stores().stream().filter(c -> c.name().equals(store)).mapToLong(DealStoreChipView::count).sum();
    }

    @Test
    void 규칙5_핫딜_글_중단은_게시비를_빼고_노출_자리_몫만_돌려준다() {
        Booked b = postedBooking("HOME_TOP");
        long postId = postOf(b.id());
        AppRow a = app(b.id());
        long postSupply = store.items(b.id()).stream().filter(i -> i.product() == com.community.bizapplication.entity.BizProduct.HOTDEAL_POST)
                .mapToLong(i -> i.supply()).sum();
        long allSupply = store.items(b.id()).stream().mapToLong(i -> i.supply()).sum();
        long base = Math.floorDiv(a.approved().total() * (allSupply - postSupply), allSupply);
        assertThat(view.detail(b.user(), b.id()).pause().proratableTotal()).isEqualTo(base);
        pauses.stop(b.user(), b.id(), "판매 종료");
        RefundRow f = openRefund(b.id());
        long prorated = BizPricing.prorate(base, a.startAt(), a.endAt(), clock.instant()).proposed();
        assertThat(f.proratedAmount()).isEqualTo(prorated).isLessThan(a.approved().total());
        assertThat(f.proposedAmount()).isEqualTo(prorated - fee(prorated, a.stopFeeRate()));
        assertThat(postRow(postId).get("hidden_reason")).isEqualTo("SELLER_STOPPED");
        assertThatThrownBy(() -> postQueries.detail(Long.toString(postId), null, "127.0.0.1")).isInstanceOfSatisfying(DealSellerHiddenException.class,
                e -> assertThat(e.getExtras()).containsEntry("state", "STOPPED"));
        assertThat(view.detail(b.user(), b.id()).post().placement().stopReason()).isEqualTo("POST_HIDDEN");
    }

    @Test
    void 규칙5_노출_없는_핫딜_글_중단은_0원이라_환불_줄이_없다() {
        Booked b = postedBooking("NONE");
        assertThat(view.detail(b.user(), b.id()).pause().proratableTotal()).isZero();
        pauses.pauseByOperator(operator, b.id(), "PHONE", "전화 요청");
        pauses.stopByOperator(operator, b.id(), "PHONE", "전화로 중단 요청", null);
        var hiddenRow = dealPostStore.sellerHidden(List.of(postOf(b.id()))).get(postOf(b.id()));
        assertThat(hiddenRow.cause()).isEqualTo("ADVERTISER_STOP");
        assertThat(hiddenRow.byOperator()).isTrue();
        assertThat(refundRows(b.id())).isZero();
        assertThat(events(b.id())).contains("STOPPED").doesNotContain("REFUND_REQUESTED");
        AppRow a = app(b.id());
        assertThat(a.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(a.takedownCause()).isEqualTo(BizTakedownCause.ADVERTISER_STOP);
        assertThat(postRow(postOf(b.id())).get("hidden_reason")).isEqualTo("SELLER_STOPPED");
        assertThat(eventNote(b.id(), "STOPPED")).isEqualTo("FROM_PAUSE · PHONE · 전화로 중단 요청");
        assertThat(view.detail(b.user(), b.id()).history()).filteredOn(h -> h.action().equals("STOPPED")).singleElement()
                .satisfies(h -> assertThat(h.note()).isEqualTo("FROM_PAUSE · PHONE"));
    }

    @Test
    void 규칙9_운영자가_따로_숨긴_글은_다시_켜기가_풀지_않는다() {
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        pauses.pause(b.user(), b.id());
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE posts SET hidden_at = now() + interval '1 second', hidden_reason = 'STAFF' WHERE id = ?", postId); // 운영자가 따로 숨겼다
        pauses.resume(b.user(), b.id());
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(postRow(postId).get("hidden_reason")).isEqualTo("STAFF");
        assertThat(events(b.id())).doesNotContain("POST_RESTORED");
    }

    @Test
    void 핫딜_글은_품절로_종료했거나_운영_숨김이면_정지_중단할_수_없다() {
        Booked ended = postedBooking("NONE");
        view.endDeal(ended.user(), ended.id());
        assertThat(codeOf(() -> pauses.pause(ended.user(), ended.id()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> pauses.stop(ended.user(), ended.id(), "중단"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        Booked hidden = postedBooking("NONE");
        boardFixture.staffHide(postOf(hidden.id()));
        assertThat(codeOf(() -> pauses.pause(hidden.user(), hidden.id()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        // 게시 전(시작 전)도 없다
        Booked waiting = booked("NONE");
        assertThat(codeOf(() -> pauses.pause(waiting.user(), waiting.id()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }
}

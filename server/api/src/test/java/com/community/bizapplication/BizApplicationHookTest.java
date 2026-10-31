package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.service.BizPeriods;
import com.community.business.dto.BusinessSuspendPlan;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.service.BusinessApplicationHook;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 사업자 신원 → 신청 건 훅(H2 · F8 · 설계 9절) — 영향 건수·서비스 카드 건수, 정지 처리 선택별(결제 전 취소/유지 · 시작 전 환불/보류 · 진행 중 내림+일할/내림만), 해제(연장 후 되돌리기 ·
 * 꽉 차면 늘리지 않음 · 환불 유지), 탈퇴 판정·처리(포기 동의).
 */
class BizApplicationHookTest extends BizApplicationApiTestBase {

    private record Set3(long user, long profile, long pending, long upcoming, long running, BizPeriods.Period runningPeriod) {
    }

    /** 결제 대기 하나 · 시작 전 예약 하나 · 진행 중 예약 하나(시계를 진행 중 건의 시작 뒤로 옮긴다) */
    private Set3 three() {
        long user = approvedBusiness();
        long profile = profileOf(user);
        long running = submitTextLine(user, 0, 7, "ONLINE");
        BizPeriods.Period rp = freshPeriod(7);
        approve(running, "ONLINE", rp);
        pay(user, running);
        long upcoming = submitDeal(user, "NONE", null, null, "ONLINE");
        BizPeriods.Period up = new BizPeriods.Period(rp.start().plus(Duration.ofDays(20)), rp.start().plus(Duration.ofDays(22)));
        review.approve(operator, upcoming, "ONLINE", quoteSupply(upcoming, up), null, clock.instant().plus(Duration.ofHours(72)).toString(), up.start().toString(),
                up.end().toString());
        pay(user, upcoming);
        clock.set(rp.start().plus(Duration.ofDays(2)));
        long pending = submitTextLine(user, 0, 3, "ONLINE");
        approve(pending, "ONLINE", freshPeriod(3));
        return new Set3(user, profile, pending, upcoming, running, rp);
    }

    private void suspend(long profile, BusinessSuspendPlan plan) {
        tx.executeWithoutResult(s -> hook.onSuspended(profile, plan, operator.operatorId()));
    }

    @Test
    void 영향_건수와_서비스_카드_건수() {
        Set3 s = three();
        assertThat(hook.impact(s.profile())).isEqualTo(new BusinessApplicationHook.SuspendImpact(1, 1, 1));
        assertThat(hook.counts(s.profile(), BusinessServiceKind.AD)).isEqualTo(new BusinessApplicationHook.ServiceCounts(1, 1));
        assertThat(hook.counts(s.profile(), BusinessServiceKind.HOTDEAL)).isEqualTo(new BusinessApplicationHook.ServiceCounts(1, 0));
    }

    @Test
    void 정지_기본값_결제_전_취소_시작_전_전액_환불_진행_중_내림과_일할_환불_심사() {
        Set3 s = three();
        suspend(s.profile(), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.CANCEL, BusinessSuspendPlan.Scheduled.REFUND_FULL,
                BusinessSuspendPlan.Running.TAKEDOWN_PRORATE));
        assertThat(app(s.pending()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(app(s.upcoming()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(s.upcoming()).orElseThrow().type()).isEqualTo(BizRefundType.FULL_AUTO);
        AppRow running = app(s.running());
        assertThat(running.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(running.takedownCause()).isEqualTo(BizTakedownCause.SUSPENDED);
        var r = refunds.latest(s.running()).orElseThrow();
        assertThat(r.type()).isEqualTo(BizRefundType.SUSPEND_PRORATED);
        assertThat(r.status()).isEqualTo(BizRefundStatus.REQUESTED);
        assertThat(r.proposedAmount()).isPositive();
    }

    @Test
    void 정지_결제_전_유지_시작_전_보류_진행_중_내림만() {
        Set3 s = three();
        suspend(s.profile(), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD, BusinessSuspendPlan.Running.TAKEDOWN_ONLY));
        assertThat(app(s.pending()).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(app(s.upcoming()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(refunds.exists(s.running())).isFalse();
        assertThat(refunds.exists(s.upcoming())).isFalse();
    }

    @Test
    void 정지_결제_전_취소_때_입금_기록이_있으면_수동_환불_송금_대기() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "MANUAL");
        approve(id, "MANUAL", freshPeriod(3));
        review.saveDeposit(operator, id, app(id).approved().total(), today().toString(), "시험상사", null);
        suspend(profileOf(user), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.CANCEL, BusinessSuspendPlan.Scheduled.REFUND_FULL,
                BusinessSuspendPlan.Running.TAKEDOWN_PRORATE));
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(id).orElseThrow().transferStatus()).isEqualTo(BizRefundStore.TRANSFER_PENDING);
    }

    @Test
    void 해제_연장_후_되돌리기는_내린_시간만큼_끝을_늘리고_정지로_생긴_환불_심사는_거절() {
        Set3 s = three();
        suspend(s.profile(), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD, BusinessSuspendPlan.Running.TAKEDOWN_PRORATE));
        Instant takenDown = app(s.running()).takenDownAt();
        clock.set(takenDown.plus(Duration.ofHours(30)));
        tx.executeWithoutResult(x -> hook.onUnsuspended(s.profile(), BusinessSuspendPlan.Restore.EXTEND_RESTORE, operator.operatorId()));
        AppRow running = app(s.running());
        assertThat(running.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(running.endAt()).isEqualTo(s.runningPeriod().end().plus(Duration.ofHours(30)));
        assertThat(store.items(s.running()).get(0).endAt()).isEqualTo(running.endAt());
        assertThat(refunds.latest(s.running()).orElseThrow().status()).isEqualTo(BizRefundStatus.DENIED);
        // 시작 전에 보류됐던 건은 아직 시작 전이라 늘리지 않고 되돌린다
        assertThat(app(s.upcoming()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(app(s.upcoming()).endAt()).isEqualTo(store.items(s.upcoming()).get(0).endAt());
    }

    @Test
    void 해제_연장할_자리가_없으면_늘리지_않고_되돌린_뒤_기록한다() {
        long user = approvedBusiness();
        long id = submitBanner(user, "WIDE", 0, 2, "ONLINE");
        BizPeriods.Period p = freshPeriod(2);
        approve(id, "ONLINE", p);
        pay(user, id);
        // 끝 바로 뒤 하루를 다른 두 건이 차지(넓은 배너 한도 2)
        BizPeriods.Period after = new BizPeriods.Period(p.end(), p.end().plus(Duration.ofDays(1)));
        for (int i = 0; i < 2; i++) {
            long other = submitBanner(approvedBusiness(), "WIDE", 0, 1, "ONLINE");
            review.approve(operator, other, "ONLINE", quoteSupply(other, after), null, clock.instant().plus(Duration.ofHours(72)).toString(), after.start().toString(),
                    after.end().toString());
        }
        clock.set(p.start().plus(Duration.ofHours(5)));
        suspend(profileOf(user), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD, BusinessSuspendPlan.Running.TAKEDOWN_ONLY));
        clock.advance(Duration.ofHours(4));
        tx.executeWithoutResult(x -> hook.onUnsuspended(profileOf(user), BusinessSuspendPlan.Restore.EXTEND_RESTORE, operator.operatorId()));
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(app(id).endAt()).isEqualTo(p.end());
        assertThat(store.events(id)).anyMatch(e -> e.action().equals("RESTORED") && "EXTEND_FAILED".equals(e.note()));
        assertThat(BizProduct.WIDE_BANNER.banner()).isTrue();
    }

    @Test
    void 해제_환불_유지는_심사_대기_환불이_있는_건을_내린_채_둔다() {
        Set3 s = three();
        suspend(s.profile(), new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD, BusinessSuspendPlan.Running.TAKEDOWN_PRORATE));
        tx.executeWithoutResult(x -> hook.onUnsuspended(s.profile(), BusinessSuspendPlan.Restore.KEEP_REFUND, operator.operatorId()));
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(refunds.latest(s.running()).orElseThrow().status()).isEqualTo(BizRefundStatus.REQUESTED);
        // 환불 심사로 정리 — 내린 건도 승인하면 취소
        review.approveRefund(operator, s.running(), 0L, "정책 위반 — 환불 없음");
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(app(s.upcoming()).status()).isEqualTo(BizApplicationStatus.SCHEDULED); // 환불이 없던 보류 건은 되돌린다(가정)
    }

    @Test
    void 탈퇴는_진행_중_건이_있으면_막고_포기하면_진행_중_건은_환불_없이_취소_대기와_시작_전은_정리() {
        Set3 s = three();
        assertThat(hook.withdrawalCheck(s.profile())).isEqualTo(new BusinessApplicationHook.WithdrawalCheck(true, 1));
        tx.executeWithoutResult(x -> hook.onWithdrawn(s.profile(), true));
        assertThat(app(s.pending()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(app(s.upcoming()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(s.upcoming()).orElseThrow().type()).isEqualTo(BizRefundType.FULL_AUTO);
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.exists(s.running())).isFalse();
        assertThat(store.events(s.running())).anyMatch(e -> e.action().equals("FORFEITED"));
        assertThat(hook.withdrawalCheck(s.profile())).isEqualTo(BusinessApplicationHook.WithdrawalCheck.FREE);
    }
}

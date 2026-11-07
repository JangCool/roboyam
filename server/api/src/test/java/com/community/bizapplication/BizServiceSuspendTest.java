package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizPeriods;
import com.community.business.dto.BusinessSuspendPlan;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.service.BusinessApplicationHook;
import com.community.business.service.BusinessServiceRows;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 서비스만 정지·해제(business-contract 「서비스만 정지·해제」 2026-11-02) — 신원 정지와 같은 처리 선택을 그 서비스 신청 건에만 · 해제도 그 서비스 건만 · 영향 건수는 그 서비스만 ·
 * 신원 해제는 서비스만 정지된 줄과 그 서비스 건을 되살리지 않는다 · 정지된 서비스로는 새 신청 409 {@code BUSINESS_SERVICE_SUSPENDED}(다른 서비스는 그대로).
 */
class BizServiceSuspendTest extends BizApplicationApiTestBase {

    private static final BusinessSuspendPlan STRICT = new BusinessSuspendPlan(BusinessSuspendPlan.Pending.CANCEL, BusinessSuspendPlan.Scheduled.REFUND_FULL,
            BusinessSuspendPlan.Running.TAKEDOWN_PRORATE);
    private static final BusinessSuspendPlan HOLD = new BusinessSuspendPlan(BusinessSuspendPlan.Pending.KEEP, BusinessSuspendPlan.Scheduled.HOLD,
            BusinessSuspendPlan.Running.TAKEDOWN_PRORATE);

    @Autowired
    private BusinessServiceRows serviceRows;

    /** 광고 결제 대기 하나 · 광고 진행 중 하나 · 핫딜 시작 전 예약 하나(시계는 진행 중 건의 시작 뒤) */
    private record Set3(long user, long profile, long adPending, long adRunning, long dealUpcoming, BizPeriods.Period runningPeriod) {
    }

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
        return new Set3(user, profile, pending, running, upcoming, rp);
    }

    @Test
    void 서비스_영향_건수는_그_서비스_건만_센다() {
        Set3 s = three();
        assertThat(hook.impact(s.profile(), BusinessServiceKind.AD)).isEqualTo(new BusinessApplicationHook.SuspendImpact(1, 0, 1));
        assertThat(hook.impact(s.profile(), BusinessServiceKind.HOTDEAL)).isEqualTo(new BusinessApplicationHook.SuspendImpact(0, 1, 0));
        assertThat(hook.impact(s.profile())).isEqualTo(new BusinessApplicationHook.SuspendImpact(1, 1, 1));
    }

    @Test
    void 핫딜만_정지하면_핫딜_예약만_전액_환불하고_광고_건은_그대로() {
        Set3 s = three();
        tx.executeWithoutResult(x -> hook.onServiceSuspended(s.profile(), BusinessServiceKind.HOTDEAL, STRICT, operator.operatorId()));
        assertThat(app(s.dealUpcoming()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(s.dealUpcoming()).orElseThrow().type()).isEqualTo(BizRefundType.FULL_AUTO);
        assertThat(app(s.adPending()).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(app(s.adRunning()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refunds.exists(s.adRunning())).isFalse();
    }

    @Test
    void 광고만_정지하면_광고_대기_취소_진행_중_내림과_일할_환불_심사_핫딜_예약은_그대로() {
        Set3 s = three();
        tx.executeWithoutResult(x -> hook.onServiceSuspended(s.profile(), BusinessServiceKind.AD, STRICT, operator.operatorId()));
        assertThat(app(s.adPending()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        AppRow running = app(s.adRunning());
        assertThat(running.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(running.takedownCause()).isEqualTo(BizTakedownCause.SUSPENDED);
        assertThat(refunds.latest(s.adRunning()).orElseThrow().type()).isEqualTo(BizRefundType.SUSPEND_PRORATED);
        assertThat(app(s.dealUpcoming()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refunds.exists(s.dealUpcoming())).isFalse();
    }

    @Test
    void 서비스_해제는_그_서비스_건만_연장_후_되돌리고_정지로_생긴_환불_심사는_거절() {
        Set3 s = three();
        tx.executeWithoutResult(x -> hook.onSuspended(s.profile(), HOLD, operator.operatorId())); // 신원 정지로 둘 다 내림
        alone(s.profile(), "HOTDEAL"); // 핫딜은 서비스만 정지로 남은 상태라고 둔다
        Instant takenDown = app(s.adRunning()).takenDownAt();
        clock.set(takenDown.plus(Duration.ofHours(30)));
        tx.executeWithoutResult(x -> hook.onServiceResumed(s.profile(), BusinessServiceKind.AD, BusinessSuspendPlan.Restore.EXTEND_RESTORE, operator.operatorId()));
        AppRow running = app(s.adRunning());
        assertThat(running.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(running.endAt()).isEqualTo(s.runningPeriod().end().plus(Duration.ofHours(30)));
        assertThat(refunds.latest(s.adRunning()).orElseThrow().status()).isEqualTo(BizRefundStatus.DENIED);
        assertThat(app(s.dealUpcoming()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN); // 핫딜 건은 그대로
    }

    @Test
    void 신원_해제는_서비스만_정지된_줄과_그_서비스_건을_되살리지_않는다() {
        Set3 s = three();
        tx.executeWithoutResult(x -> {
            hook.onServiceSuspended(s.profile(), BusinessServiceKind.AD, HOLD, operator.operatorId());
        });
        alone(s.profile(), "AD");
        assertThat(app(s.adRunning()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        // 신원 정지 → 해제(신원 처리와 같은 순서 — 줄 맞추기 뒤 훅)
        tx.executeWithoutResult(x -> {
            serviceRows.sync(s.profile(), BusinessServiceStatus.SUSPENDED, operator.operatorId(), clock.instant());
            hook.onSuspended(s.profile(), HOLD, operator.operatorId());
        });
        assertThat(app(s.dealUpcoming()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        clock.set(clock.instant().plus(Duration.ofHours(3)));
        tx.executeWithoutResult(x -> {
            serviceRows.move(s.profile(), EnumSet.of(BusinessServiceStatus.SUSPENDED), BusinessServiceStatus.APPROVED, operator.operatorId(), clock.instant());
            hook.onUnsuspended(s.profile(), BusinessSuspendPlan.Restore.EXTEND_RESTORE, operator.operatorId());
        });
        assertThat(lineStatus(s.profile(), "HOTDEAL")).isEqualTo("APPROVED");
        assertThat(lineStatus(s.profile(), "AD")).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT suspend_code FROM business_services WHERE profile_id = ? AND service = 'AD'", String.class, s.profile())).isEqualTo("OTHER");
        assertThat(app(s.dealUpcoming()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(app(s.adRunning()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(refunds.latest(s.adRunning()).orElseThrow().status()).isEqualTo(BizRefundStatus.REQUESTED); // 해제로 거절되지 않는다
        // 반려·탈퇴(닫힘)는 서비스만 정지 표시도 지운다
        tx.executeWithoutResult(x -> serviceRows.sync(s.profile(), BusinessServiceStatus.OFF, null, clock.instant()));
        assertThat(lineStatus(s.profile(), "AD")).isEqualTo("OFF");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_services WHERE profile_id = ? AND suspend_code IS NOT NULL", Integer.class, s.profile())).isZero();
    }

    @Test
    void 정지된_서비스로는_새_신청_409_BUSINESS_SERVICE_SUSPENDED_다른_서비스는_그대로() {
        long user = approvedBusiness();
        alone(profileOf(user), "AD");
        assertThat(codeOf(() -> submitTextLine(user, 0, 3, "ONLINE"))).isEqualTo("BUSINESS_SERVICE_SUSPENDED");
        assertThat(submitDeal(user, "NONE", null, null, "ONLINE")).isPositive();
        // 신원이 정지면 서비스 줄과 상관없이 그대로 BUSINESS_NOT_APPROVED
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_profiles SET status = 'SUSPENDED', suspend_code = 'OTHER', suspend_text = '시험', suspended_at = now(), suspend_pending_action = 'KEEP', "
                + "suspend_scheduled_action = 'HOLD', suspend_running_action = 'TAKEDOWN_ONLY' WHERE id = ?", profileOf(user));
        assertThat(codeOf(() -> submitDeal(user, "NONE", null, null, "ONLINE"))).isEqualTo("BUSINESS_NOT_APPROVED");
    }

    /** 그 서비스 줄을 「서비스만 정지」로(관리자 API 는 admin 시험이 막는다) */
    private void alone(long profileId, String service) {
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_services SET status = 'SUSPENDED', suspend_code = 'OTHER', suspend_text = '시험 정지', suspended_at = now() WHERE profile_id = ? AND service = ?",
                profileId, service);
    }

    private String lineStatus(long profileId, String service) {
        return jdbc.queryForObject("SELECT status FROM business_services WHERE profile_id = ? AND service = ?", String.class, profileId, service);
    }
}

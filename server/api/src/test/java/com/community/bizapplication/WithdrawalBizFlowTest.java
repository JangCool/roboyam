package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.audit.dto.AuditActions;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizApplicationOps;
import com.community.bizapplication.service.BizPauseService;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizPricing;
import com.community.withdrawal.WithdrawalTestSupport;
import com.community.withdrawal.batch.WithdrawalAutoCompleteJob;
import com.community.withdrawal.dto.request.WithdrawalRequest;
import com.community.withdrawal.dto.response.WithdrawalResult;
import com.community.withdrawal.dto.response.WithdrawalView;
import com.community.withdrawal.service.WithdrawalService;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원 탈퇴 × 사업자 광고·핫딜 신청 건(commerce.md 17-1, business-contract F8) — 진행 중 건이 있을 때 「기다리기」는 남은 일 {@code BIZ_APPLICATION}, 「포기」는 일할 금액 동의 뒤 즉시 완료와
 * {@code BusinessWithdrawal.withdraw(forfeitRunning = true)} 효과. 결제 대기·시작 전 건은 남은 일이 아니고 자동 취소·전액 환불 경로를 탄다. 재료는 {@link BizApplicationApiTestBase}.
 */
class WithdrawalBizFlowTest extends BizApplicationApiTestBase {

    private static final String IP = "127.0.0.1";

    @Autowired
    private WithdrawalService service;
    @Autowired
    private WithdrawalAutoCompleteJob job;
    @Autowired
    private BizApplicationOps ops;
    @Autowired
    private BizPauseService pauses;
    @Autowired
    private PasswordEncoder encoder;

    /** 결제 대기 하나 · 시작 전 예약 하나 · 진행 중 예약 하나(시계는 진행 중 건의 시작 2일 뒤) — {@code BizApplicationHookTest.three()} 와 같다 */
    private record Set3(long user, long profile, long pending, long upcoming, long running, BizPeriods.Period runningPeriod) {
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
        WithdrawalTestSupport.withPassword(jdbc, encoder, user, clock.instant()); // 시계를 옮긴 뒤 「방금 로그인」
        return new Set3(user, profile, pending, upcoming, running, rp);
    }

    private long expectedForfeit(long applicationId) {
        AppRow app = app(applicationId);
        long paid = ops.paidAmount(app);
        return Math.max(0, Math.min(BizPricing.prorate(paid, app.startAt(), app.endAt(), clock.instant()).proposed(), paid - refunds.approvedTotal(applicationId)));
    }

    private String status(long userId) {
        return memberFixture.status(userId);
    }

    private WithdrawalAutoCompleteJob.Outcome process(long withdrawalId) {
        return tx.execute(x -> job.process(withdrawalId));
    }

    private long withdrawalId(long userId) {
        return jdbc.queryForObject("SELECT id FROM member_withdrawals WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, userId);
    }

    @Test
    void 화면은_진행_중_건과_일할_포기액_환불_대기_합을_보이고_결제_대기_시작_전_건은_남은_일이_아니다() {
        Set3 s = three();

        WithdrawalView view = service.view(s.user());

        assertThat(view.business()).isNotNull();
        assertThat(view.business().running()).hasSize(1);
        WithdrawalView.BizRunning running = view.business().running().get(0);
        assertThat(running.id()).isEqualTo(Long.toString(s.running()));
        assertThat(running.kind()).isEqualTo("AD");
        assertThat(running.endsAt()).isEqualTo(app(s.running()).endAt());
        assertThat(running.forfeitAmount()).isEqualTo(expectedForfeit(s.running())).isGreaterThan(0);
        assertThat(view.business().forfeitTotal()).isEqualTo(running.forfeitAmount());
        assertThat(view.business().refundWaitingTotal()).isZero();
        assertThat(view.pending()).isEmpty(); // 광고는 선택(기다리기)에 달린 일이라 고정 목록에는 없다
        assertThat(view.remains().hasPaymentHistory()).isTrue(); // 광고 결제 = 결제 이력
    }

    @Test
    void 기다리기면_BIZ_APPLICATION_으로_예약되고_광고가_끝나면_배치가_완료하며_시작_전_건은_전액_환불_취소() {
        Set3 s = three();

        WithdrawalResult result = service.request(s.user(), WithdrawalTestSupport.request(null, null, "WAIT", null, null), IP);

        assertThat(result.state()).isEqualTo("SCHEDULED");
        assertThat(result.pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("BIZ_APPLICATION");
        assertThat(result.pending().get(0).link()).isEqualTo("/business/applications/" + s.running());
        assertThat(result.expectedCompleteAt()).isEqualTo(app(s.running()).endAt());
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.SCHEDULED); // 진행 중 건은 그대로 노출된다
        long id = withdrawalId(s.user());
        assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);

        clock.set(app(s.running()).endAt().plus(Duration.ofHours(1)));
        assertThat(process(id)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);

        assertThat(status(s.user())).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT status FROM business_profiles WHERE id = ?", String.class, s.profile())).isEqualTo("WITHDRAWN");
        assertThat(app(s.pending()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(app(s.upcoming()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(s.upcoming()).orElseThrow().type()).isEqualTo(BizRefundType.FULL_AUTO);
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.SCHEDULED); // 끝난 건은 포기가 아니라 그대로(기간이 끝났다)
        assertThat(store.events(s.running())).noneMatch(e -> e.action().equals("FORFEITED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawn_member_contacts WHERE user_id = ?", Integer.class, s.user())).isEqualTo(1);
    }

    @Test
    void 포기면_동의_금액이_일할_계산과_같아야_하고_같으면_즉시_완료되며_신원_탈퇴_서비스_OFF_진행_중_건_FORFEITED() {
        Set3 s = three();
        long forfeit = expectedForfeit(s.running());

        ValidationException wrong = catchThrowableOfType(ValidationException.class,
                () -> service.request(s.user(), WithdrawalTestSupport.request(null, null, "FORFEIT", null, new WithdrawalRequest.ForfeitConsent(null, null, forfeit - 1)), IP));
        assertThat(wrong.getFields()).containsKey("forfeitConsent");
        assertThat(status(s.user())).isEqualTo("ACTIVE");

        WithdrawalResult result = service.request(s.user(), WithdrawalTestSupport.request(null, null, "FORFEIT", null, new WithdrawalRequest.ForfeitConsent(null, null, forfeit)), IP);

        assertThat(result.state()).isEqualTo("COMPLETED");
        assertThat(status(s.user())).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT status FROM business_profiles WHERE id = ?", String.class, s.profile())).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForList("SELECT status FROM business_services WHERE profile_id = ?", String.class, s.profile())).isNotEmpty().allMatch("OFF"::equals);
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(store.events(s.running())).anyMatch(e -> e.action().equals("FORFEITED"));
        assertThat(refunds.exists(s.running())).isFalse(); // 진행 중 건은 환불 없이 포기
        assertThat(app(s.pending()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(app(s.upcoming()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(s.upcoming()).orElseThrow().type()).isEqualTo(BizRefundType.FULL_AUTO);
        Map<String, Object> row = jdbc.queryForMap("SELECT forfeit_biz_amount, forfeit_consented_at, biz_choice FROM member_withdrawals WHERE user_id = ?", s.user());
        assertThat(((Number) row.get("forfeit_biz_amount")).longValue()).isEqualTo(forfeit);
        assertThat(row.get("forfeit_consented_at")).isNotNull();
        assertThat(row.get("biz_choice")).isEqualTo("FORFEIT");
        Map<String, Object> audit = lastAudit("MEMBER", Long.toString(s.user()), AuditActions.MEMBER_WITHDRAW);
        assertThat(String.valueOf(audit.get("detail"))).contains("\"forfeitBizAmount\":" + forfeit).contains("\"forfeitBiz\":true");
    }

    @Test
    void 열린_일할_환불_심사가_있으면_환불_대기_합에_보이고_포기하면_거절_처리된다() {
        Set3 s = three();
        // 광고주가 진행 중 건의 중단을 요청해 일할 환불 심사가 열린다(운영자 처리 전)
        pauses.stop(s.user(), s.running(), "더 쓰지 않아요");
        assertThat(refunds.latest(s.running()).orElseThrow().status()).isEqualTo(BizRefundStatus.REQUESTED);
        long waiting = refunds.latest(s.running()).orElseThrow().proposedAmount();

        // 중단한 건은 끝나지 않은 내림(TAKEN_DOWN) — 신청 건 훅의 「진행 중」 판정과 같아 포기 대상이고, 환불 대기 금액은 따로 보인다
        WithdrawalView view = service.view(s.user());
        assertThat(view.business()).isNotNull();
        assertThat(view.business().refundWaitingTotal()).isEqualTo(waiting);
        assertThat(view.business().running()).hasSize(1);
        assertThat(app(s.running()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);

        // 기다리기(기본)면 남은 일 — 환불 심사는 운영자 몫이라 그대로
        assertThat(service.request(s.user(), WithdrawalTestSupport.plain(), IP).pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("BIZ_APPLICATION");
        assertThat(refunds.latest(s.running()).orElseThrow().status()).isEqualTo(BizRefundStatus.REQUESTED);
        service.cancel(s.user(), IP);

        // 포기하면 훅이 열린 환불 심사를 거절(FORFEITED)하고 건을 닫는다
        long forfeit = view.business().forfeitTotal();
        assertThat(service.request(s.user(), WithdrawalTestSupport.request(null, null, "FORFEIT", null, new WithdrawalRequest.ForfeitConsent(null, null, forfeit)), IP).state())
                .isEqualTo("COMPLETED");
        assertThat(refunds.latest(s.running()).orElseThrow().status()).isEqualTo(BizRefundStatus.DENIED);
        assertThat(store.events(s.running())).anyMatch(e -> e.action().equals("FORFEITED"));
    }
}

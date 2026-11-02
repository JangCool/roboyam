package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizPauseService;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizRefundSummary;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * 운영자 추가 환불(클레임 — biz-pause-contract 「추가 환불(클레임)」, V128 · commerce.md 18절 — 돈 규칙이라 모두 시험으로 막는다).
 *
 * <ul>
 *   <li>대상: 취소됨 · 끝난 건 · 광고주 중단(환불 거절 뒤)은 된다 / 시작 전 · 진행 중 · 정지 중 · 긴급 내림 중 · 열린 환불 · 결제 전은 409.</li>
 *   <li>입력: 0원·음수·없음·남은 가능액 초과 400 {@code fields.amount} · 사유 5~200자 400 {@code fields.reason}.</li>
 *   <li>돈: 온라인 = 입력 금액 그대로 결제 모듈 부분 취소 · 수동 = 송금 대기 → 송금 완료 표시 · 여러 번 나눠도 합계 ≤ 결제액 · 동시 두 요청도 남은 가능액을 넘지 않는다(행 잠금).</li>
 *   <li>합계 {@code refundSummary}({@code feeKept}) · 처리 기록 {@code CLAIM_REFUNDED} · 감사 MONEY · 사업자 알림 · 공개 상세에 CLAIM 이 보이고 사유는 안 보인다.</li>
 * </ul>
 */
class BizRefundClaimTest extends BizHotdealTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private BizPauseService pauses;
    @Autowired
    private BizApplicationReader reader;

    private record Paid(long user, long id, BizPeriods.Period period) {
    }

    /** 결제한 한 줄 광고(온라인, {@code days} 일) — 시계는 아직 시작 전 */
    private Paid paidText(int days) {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, days, "ONLINE");
        BizPeriods.Period p = freshPeriod(days);
        approve(id, "ONLINE", p);
        pay(user, id);
        return new Paid(user, id, p);
    }

    /** 수동 결제 확정까지(입금 확인 = 확정 금액) */
    private Paid paidManual(int days) {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, days, "MANUAL");
        BizPeriods.Period p = freshPeriod(days);
        approve(id, "MANUAL", p);
        confirmManual(id);
        return new Paid(user, id, p);
    }

    private void at(Paid p, Duration fromStart) {
        clock.set(p.period().start().plus(fromStart));
    }

    /** 진행 중 중단 → 제안대로 환불 승인(CANCELED) — 승인한 중단 환불 줄 */
    private RefundRow stoppedAndApproved(Paid p) {
        at(p, Duration.ofHours(3));
        pauses.stop(p.user(), p.id(), "행사 취소");
        RefundRow open = refunds.lockOpen(p.id()).orElseThrow();
        review.approveRefund(operator, p.id(), open.proposedAmount(), "제안대로");
        assertThat(app(p.id()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        return refunds.all(p.id()).stream().filter(r -> r.type() == BizRefundType.STOP_PRORATED).findFirst().orElseThrow();
    }

    private long paid(long id) {
        return app(id).approved().total();
    }

    private BizRefundSummary summary(long id) {
        return reader.refundSummary(app(id), store.deposit(id).orElse(null));
    }

    private void claim(long id, long amount) {
        review.claimRefund(operator, id, amount, "고객 클레임 — 전액 보상");
    }

    private String stateCode(long id) {
        return codeOf(() -> claim(id, 1));
    }

    // ── 대상 상태 ───────────────────────────────────────────────────────────

    @Test
    void 시작_전_진행_중_정지_중_긴급_내림_중_열린_환불_결제_전은_409() {
        Paid upcoming = paidText(7);
        assertThat(stateCode(upcoming.id())).as("시작 전").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        Paid running = paidText(7);
        at(running, Duration.ofHours(1));
        assertThat(stateCode(running.id())).as("진행 중").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        Paid paused = paidText(7);
        at(paused, Duration.ofHours(1));
        pauses.pause(paused.user(), paused.id());
        assertThat(stateCode(paused.id())).as("정지 중").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        Paid emergency = paidText(7);
        at(emergency, Duration.ofHours(1));
        ads.takedown(operator, emergency.id(), "링크 사고", false);
        assertThat(stateCode(emergency.id())).as("긴급 내림 중(끝 전)").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        Paid open = paidText(7);
        at(open, Duration.ofHours(3));
        pauses.stop(open.user(), open.id(), "중단");
        assertThat(refunds.lockOpen(open.id())).isPresent();
        assertThat(stateCode(open.id())).as("열린 환불(중단 심사 중)").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        long user = approvedBusiness();
        long unpaid = submitTextLine(user, 0, 7, "ONLINE");
        approve(unpaid, "ONLINE", freshPeriod(7));
        assertThat(stateCode(unpaid)).as("결제 대기").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        // 막힌 건은 환불 줄·처리 기록이 생기지 않는다
        for (long id : List.of(upcoming.id(), running.id(), paused.id(), emergency.id(), unpaid)) {
            assertThat(refunds.all(id)).noneMatch(r -> r.type() == BizRefundType.CLAIM);
            assertThat(eventNote(id, "CLAIM_REFUNDED")).isNull();
        }
        assertThat(app(running.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }

    @Test
    void 취소됨_끝난_예약_끝난_내림_광고주_중단_환불_거절_뒤는_되고_상태는_그대로() {
        Paid canceled = paidText(4);
        stoppedAndApproved(canceled);
        claim(canceled.id(), 100);
        assertThat(app(canceled.id()).status()).isEqualTo(BizApplicationStatus.CANCELED);

        Paid ended = paidText(3);
        at(ended, Duration.ofDays(3)); // 끝 시각 = 지금
        claim(ended.id(), 100);
        assertThat(app(ended.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);

        Paid endedDown = paidText(3);
        at(endedDown, Duration.ofHours(1));
        ads.takedown(operator, endedDown.id(), "링크 사고", false);
        at(endedDown, Duration.ofDays(4));
        claim(endedDown.id(), 100);
        assertThat(app(endedDown.id()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);

        Paid denied = paidText(7);
        at(denied, Duration.ofHours(3));
        pauses.stop(denied.user(), denied.id(), "중단");
        review.denyRefund(operator, denied.id(), "규정상 불가");
        at(denied, Duration.ofHours(4)); // 끝 전이어도 광고주 중단 건은 된다
        claim(denied.id(), 100);
        assertThat(app(denied.id()).status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);

        for (long id : List.of(canceled.id(), ended.id(), endedDown.id(), denied.id())) {
            RefundRow c = refunds.latest(id).orElseThrow();
            assertThat(c.type()).isEqualTo(BizRefundType.CLAIM);
            assertThat(c.status()).isEqualTo(BizRefundStatus.APPROVED);
            assertThat(c.approvedAmount()).isEqualTo(100);
            assertThat(refunds.lockOpen(id)).as("바로 승인 — 열린 줄 없음").isEmpty();
        }
    }

    // ── 입력 ────────────────────────────────────────────────────────────────

    @Test
    void 금액_0원_음수_없음_남은_가능액_초과와_사유_길이는_400() {
        Paid p = paidText(4);
        RefundRow stop = stoppedAndApproved(p);
        long refundable = paid(p.id()) - stop.approvedAmount();
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), 0L, "고객 클레임 보상"))).containsExactly("amount");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), -1L, "고객 클레임 보상"))).containsExactly("amount");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), null, "고객 클레임 보상"))).containsExactly("amount");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), refundable + 1, "고객 클레임 보상"))).containsExactly("amount");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), 100L, "넷글자임"))).containsExactly("reason");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), 100L, "  "))).containsExactly("reason");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), 100L, null))).containsExactly("reason");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), 100L, "가".repeat(201)))).containsExactly("reason");
        assertThat(fieldsOf(() -> review.claimRefund(operator, p.id(), 0L, "짧음"))).containsExactlyInAnyOrder("amount", "reason");
        assertThat(refunds.all(p.id())).noneMatch(r -> r.type() == BizRefundType.CLAIM);
        // 경계값은 된다 — 사유 5자·200자, 금액 = 남은 가능액
        review.claimRefund(operator, p.id(), 1L, "다섯글자임");
        review.claimRefund(operator, p.id(), refundable - 1, "가".repeat(200));
        assertThat(summary(p.id()).refundable()).isZero();
    }

    // ── 돈 ──────────────────────────────────────────────────────────────────

    @Test
    void 온라인은_입력_금액으로_결제_모듈_부분_취소_처리_기록_감사_알림() {
        Paid p = paidText(4);
        RefundRow stop = stoppedAndApproved(p);
        int approvedNotices = memberNotices(p.user(), "REFUND_APPROVED");
        long fee = stop.feeAmount();
        assertThat(fee).isPositive();
        review.claimRefund(operator, p.id(), fee, "고객 클레임 — 수수료 돌려줌");

        RefundRow c = refunds.latest(p.id()).orElseThrow();
        assertThat(c.type()).isEqualTo(BizRefundType.CLAIM);
        assertThat(c.method()).isEqualTo(BizPaymentMethod.ONLINE);
        assertThat(c.approvedAmount()).isEqualTo(fee);
        assertThat(c.proposedAmount()).isEqualTo(fee);
        assertThat(c.paidAmount()).isEqualTo(paid(p.id()));
        assertThat(c.decisionReason()).isEqualTo("고객 클레임 — 수수료 돌려줌");
        assertThat(c.reason()).isNull();
        assertThat(c.transferStatus()).isNull();
        assertThat(c.decidedBy()).isEqualTo(operator.operatorId());
        assertThat(c.proratedAmount()).isNull();
        assertThat(c.feeAmount()).isNull();
        assertThat(c.paymentCancelId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT kind || ':' || amount || ':' || request_key FROM payment_cancels WHERE id = ?", String.class, c.paymentCancelId()))
                .isEqualTo("REFUND:" + fee + ":BIZ_REFUND:" + c.id());
        // 가짜 PG 가 커밋 뒤 바로 취소 → 처리기가 완료 시각 + REFUNDED
        assertThat(refunds.latest(p.id()).orElseThrow().completedAt()).isNotNull();
        assertThat(eventNote(p.id(), "CLAIM_REFUNDED")).isEqualTo(String.valueOf(fee));
        assertThat(eventNote(p.id(), "REFUNDED")).isEqualTo(String.valueOf(fee));
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_REFUND_CLAIM'", String.class, Long.toString(p.id())))
                .isEqualTo("MONEY");
        String detail = jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_REFUND_CLAIM'", String.class, Long.toString(p.id()));
        assertThat(detail.replace(" ", "")).contains("\"amount\":" + fee, "\"method\":\"ONLINE\"", "\"refundId\":\"" + c.id() + "\"",
                "\"refundableBefore\":" + (paid(p.id()) - stop.approvedAmount()));
        assertThat(detail).contains("고객 클레임 — 수수료 돌려줌");
        assertThat(memberNotices(p.user(), "REFUND_APPROVED")).isEqualTo(approvedNotices + 1);
    }

    @Test
    void 수동은_송금_대기로_남고_송금_완료_표시_전에는_다시_못_하며_완료_뒤_남은_만큼_된다() {
        Paid p = paidManual(3);
        at(p, Duration.ofDays(4));
        long paid = store.deposit(p.id()).orElseThrow().amount();
        review.claimRefund(operator, p.id(), 3_000L, "고객 클레임 일부 보상");
        RefundRow c = refunds.latest(p.id()).orElseThrow();
        assertThat(c.method()).isEqualTo(BizPaymentMethod.MANUAL);
        assertThat(c.transferStatus()).isEqualTo(BizRefundStore.TRANSFER_PENDING);
        assertThat(c.paymentCancelId()).isNull();
        assertThat(c.completedAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_cancels pc JOIN biz_applications a ON a.payment_order_id = pc.payment_order_id WHERE a.id = ?",
                Integer.class, p.id())).isZero();
        assertThat(stateCode(p.id())).as("송금 대기가 남아 있으면 다음 추가 환불 409").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        review.markTransferred(operator, p.id(), "국민은행 이체");
        RefundRow done = refunds.latest(p.id()).orElseThrow();
        assertThat(done.id()).isEqualTo(c.id());
        assertThat(done.transferStatus()).isEqualTo(BizRefundStore.TRANSFER_DONE);
        assertThat(done.completedAt()).isNotNull();
        assertThat(eventNote(p.id(), "REFUND_TRANSFERRED")).isEqualTo("3000");
        assertThat(memberNotices(p.user(), "REFUNDED")).isEqualTo(1);

        review.claimRefund(operator, p.id(), paid - 3_000, "고객 클레임 나머지 보상");
        assertThat(summary(p.id())).isEqualTo(new BizRefundSummary(paid, paid, 0, 0));
    }

    @Test
    void 여러_번_나눠도_합계는_결제액을_넘지_않고_마지막은_남은_만큼만_되고_그_뒤는_409() {
        Paid p = paidText(3);
        at(p, Duration.ofDays(3));
        long paid = paid(p.id());
        claim(p.id(), 1_000);
        claim(p.id(), 2_000);
        long rest = paid - 3_000;
        assertThat(fieldsOf(() -> claim(p.id(), rest + 1))).containsExactly("amount");
        claim(p.id(), rest);
        assertThat(refunds.approvedTotal(p.id())).isEqualTo(paid);
        assertThat(stateCode(p.id())).as("남은 가능액 0").isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(pc.amount), 0) FROM payment_cancels pc JOIN biz_applications a ON a.payment_order_id = pc.payment_order_id "
                + "WHERE a.id = ?", Long.class, p.id())).isEqualTo(paid);
        assertThat(store.events(p.id())).filteredOn(e -> e.action().equals("CLAIM_REFUNDED")).extracting(e -> e.note())
                .containsExactly("1000", "2000", String.valueOf(rest));
    }

    /** 수동 결제 건 — 결제 모듈 취소 가능액 검사(두 번째 방어선)가 없어 신청 건 행 잠금만이 합계를 지킨다 */
    @Test
    void 동시_두_요청은_행_잠금으로_남은_가능액을_넘지_않는다() throws Exception {
        Paid p = paidManual(3);
        at(p, Duration.ofDays(3));
        long paid = store.deposit(p.id()).orElseThrow().amount();
        long each = paid - 1_000; // 둘 다 들어가면 결제액을 넘는다
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        claim(p.id(), each);
                        outcomes.add("OK");
                    } catch (com.community.zconfiguration.exception.CommonException e) {
                        outcomes.add(e.getErrorCode());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(outcomes).hasSize(2).containsOnlyOnce("OK");
        assertThat(refunds.all(p.id())).filteredOn(r -> r.type() == BizRefundType.CLAIM).hasSize(1);
        assertThat(refunds.approvedTotal(p.id())).isEqualTo(each).isLessThanOrEqualTo(paid);
    }

    // ── 합계 · 공개 상세 ──────────────────────────────────────────────────────

    @Test
    void 합계는_결제_전_null_중단_환불_승인_뒤_수수료_몫이_있고_추가_환불만큼_줄어든다() {
        long user = approvedBusiness();
        long unpaid = submitTextLine(user, 0, 4, "ONLINE");
        approve(unpaid, "ONLINE", freshPeriod(4));
        assertThat(summary(unpaid)).as("결제 전").isNull();

        Paid p = paidText(4);
        long paid = paid(p.id());
        assertThat(summary(p.id())).isEqualTo(new BizRefundSummary(paid, 0, paid, 0));
        RefundRow stop = stoppedAndApproved(p);
        long fee = stop.feeAmount();
        long refunded = stop.approvedAmount();
        assertThat(summary(p.id())).isEqualTo(new BizRefundSummary(paid, refunded, paid - refunded, fee));
        claim(p.id(), fee - 1);
        assertThat(summary(p.id())).isEqualTo(new BizRefundSummary(paid, refunded + fee - 1, paid - refunded - fee + 1, 1));
        claim(p.id(), 5);
        assertThat(summary(p.id()).feeKept()).as("0 아래로 내려가지 않는다").isZero();
        assertThat(summary(p.id()).refundable()).isEqualTo(paid - refunded - fee - 4);
    }

    @Test
    void 공개_상세에_CLAIM_환불과_처리_기록이_보이고_클레임_사유는_안_보인다() {
        Paid p = paidText(4);
        stoppedAndApproved(p);
        String reason = "고객센터 클레임 비밀 메모 7731";
        review.claimRefund(operator, p.id(), 500L, reason);
        BizMemberView.Detail d = view.detail(p.user(), p.id());
        assertThat(d.refund().type()).isEqualTo("CLAIM");
        assertThat(d.refund().status()).isEqualTo("APPROVED");
        assertThat(d.refund().refundAmount()).isEqualTo(500);
        assertThat(d.refund().reason()).isNull();
        assertThat(d.refund().decisionReason()).isNull();
        assertThat(d.history()).filteredOn(h -> h.action().equals("CLAIM_REFUNDED")).singleElement().satisfies(h -> {
            assertThat(h.note()).isEqualTo("500");
            assertThat(h.actor()).isEqualTo("OPERATOR");
        });
        // 환불 내역 전부(오래된 차례 — 중단 환불 → 추가 환불) + 결제액·환불 합(사유는 어느 줄에도 없다)
        BizRefundSummary sum = summary(p.id());
        assertThat(d.refunds()).extracting(BizMemberView.RefundLine::type).containsExactly("STOP_PRORATED", "CLAIM");
        assertThat(d.refunds()).allSatisfy(l -> assertThat(l.status()).isEqualTo("APPROVED"));
        assertThat(d.refunds().get(1).amount()).isEqualTo(500);
        assertThat(d.refunds().stream().mapToLong(BizMemberView.RefundLine::amount).sum()).isEqualTo(sum.refunded());
        assertThat(d.refundTotals()).isEqualTo(new BizMemberView.RefundTotals(sum.paid(), sum.refunded()));
        assertThat(JSON.writeValueAsString(d)).doesNotContain("7731");
    }

    @Test
    void 결제_전이면_공개_환불_합계는_없고_내역은_빈_목록() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        BizMemberView.Detail d = view.detail(user, id);
        assertThat(d.refundTotals()).isNull();
        assertThat(d.refunds()).isEmpty();
    }
}

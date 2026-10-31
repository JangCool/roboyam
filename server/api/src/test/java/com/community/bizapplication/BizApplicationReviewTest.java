package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizSlotService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 운영자 심사 — 승인(시작 지난 기간 400 · 조정 사유 · 금액 스냅샷 · 결제 방식별 상태와 기한 · 자리 차지 · 반열림 경계) · 반려 · 이미 바뀐 상태 409 · 감사.
 */
class BizApplicationReviewTest extends BizApplicationApiTestBase {

    @Autowired
    private BizSlotService slots;

    @Test
    void 시작이_지난_기간으로는_승인하지_않는다_400_fields_period_같은_길이로_뒤로_옮기면_된다() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        AppRow a = app(id);
        clock.set(a.startAt().plus(Duration.ofHours(2))); // 심사 중 희망 시작이 지났다(F6)
        assertThat(fieldsOf(() -> review.approve(operator, id, "ONLINE", a.quote().supply(), null, clock.instant().plus(Duration.ofHours(1)).toString(),
                a.startAt().toString(), a.endAt().toString()))).containsExactly("period");
        BizPeriods.Period moved = BizPeriods.shiftedForward(new BizPeriods.Period(a.startAt(), a.endAt()), clock.instant());
        review.approve(operator, id, "ONLINE", a.quote().supply(), null, moved.start().toString(), moved.start().toString(), moved.end().toString());
        AppRow after = app(id);
        assertThat(after.status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(after.startAt()).isEqualTo(moved.start());
        assertThat(store.events(id)).anyMatch(e -> e.action().equals("APPROVED_ONLINE") && "PERIOD_MOVED".equals(e.note()));
    }

    @Test
    void 요금표와_다른_공급가는_조정_사유가_있어야_하고_승인_금액이_스냅샷으로_고정된다() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "MANUAL");
        BizPeriods.Period p = freshPeriod(14);
        long quote = quoteSupply(id, p);
        assertThat(quote).isEqualTo(5_000L * 14);
        String deadline = clock.instant().plus(Duration.ofDays(7)).toString();
        assertThat(fieldsOf(() -> review.approve(operator, id, "MANUAL", 63_001L, null, deadline, p.start().toString(), p.end().toString())))
                .containsExactly("adjustReason");
        assertThat(fieldsOf(() -> review.approve(operator, id, "MANUAL", 0L, "할인", deadline, p.start().toString(), p.end().toString()))).contains("supply");
        review.approve(operator, id, "MANUAL", 63_001L, "장기 계약 할인", deadline, p.start().toString(), p.end().toString());
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        assertThat(a.paymentMethod()).isEqualTo(BizPaymentMethod.MANUAL);
        assertThat(a.approved().supply()).isEqualTo(63_001);
        assertThat(a.approved().vat()).isEqualTo(6_300); // 버림
        assertThat(a.approved().total()).isEqualTo(69_301);
        assertThat(a.quote().supply()).isEqualTo(quote);
        assertThat(a.adjustReason()).isEqualTo("장기 계약 할인");
        assertThat(a.depositDeadlineAt()).isEqualTo(Instant.parse(deadline));
        assertThat(a.paymentDeadlineAt()).isNull();
        assertThat(store.latestCreative(id).orElseThrow().approvedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_type = 'BIZ_APPLICATION' AND target_id = ? AND action IN ('BIZ_APP_APPROVE', 'BIZ_APP_PRICE_ADJUST')",
                Integer.class, Long.toString(id))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_PRICE_ADJUST'", String.class, Long.toString(id)))
                .isEqualTo("MONEY");
    }

    @Test
    void 온라인_승인은_결제_대기와_결제_기한_결제_기한은_시작_전이어야_한다() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        BizPeriods.Period p = freshPeriod(3);
        assertThat(fieldsOf(() -> review.approve(operator, id, "ONLINE", quoteSupply(id, p), null, p.start().plus(Duration.ofHours(1)).toString(), p.start().toString(),
                p.end().toString()))).containsExactly("deadlineAt");
        assertThat(fieldsOf(() -> review.approve(operator, id, "ONLINE", quoteSupply(id, p), null, clock.instant().minusSeconds(1).toString(), p.start().toString(),
                p.end().toString()))).containsExactly("deadlineAt");
        approve(id, "ONLINE", p);
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(a.paymentDeadlineAt()).isNotNull();
        assertThat(a.approved().total()).isEqualTo(a.quote().total());
    }

    @Test
    void 승인하는_순간부터_자리를_차지하고_심사_대기는_차지하지_않는다() {
        BizPeriods.Period p = freshPeriod(2);
        long u1 = approvedBusiness();
        long a1 = submitBanner(u1, "WIDE", 0, 2, "ONLINE");
        assertThat(slots.room(com.community.bizapplication.entity.BizProduct.WIDE_BANNER, p.start(), p.end(), null).remaining()).isEqualTo(2);
        approve(a1, "ONLINE", p);
        assertThat(slots.room(com.community.bizapplication.entity.BizProduct.WIDE_BANNER, p.start(), p.end(), null).remaining()).isEqualTo(1);
        long a2 = submitBanner(approvedBusiness(), "WIDE", 0, 2, "MANUAL");
        approve(a2, "MANUAL", p);
        long a3 = submitBanner(approvedBusiness(), "WIDE", 0, 2, "ONLINE");
        assertThat(codeOf(() -> approve(a3, "ONLINE", p))).isEqualTo("BIZ_SLOT_FULL");
        assertThat(app(a3).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        // 반열림 — 앞 두 건의 끝 = 이 건의 시작이면 겹치지 않는다
        BizPeriods.Period next = new BizPeriods.Period(p.end(), p.end().plus(Duration.ofDays(1)));
        review.approve(operator, a3, "ONLINE", quoteSupply(a3, next), null, clock.instant().plus(Duration.ofHours(72)).toString(), next.start().toString(),
                next.end().toString());
        assertThat(app(a3).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        // 한 칸 앞당겨 겹치면 꽉 찬다(관리자 판정 — 자기 자신은 뺀다)
        assertThat(review.availability(a3, p.start().plus(Duration.ofHours(23)).toString(), next.end().toString()).room().remaining()).isZero();
        assertThat(review.availability(a3, next.start().toString(), next.end().toString()).room().remaining()).isEqualTo(2); // 자기 자신은 빼고 센다
    }

    @Test
    void 반려는_심사_대기만_사유_코드와_글이_필요하고_다시_처리하면_409() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        assertThat(fieldsOf(() -> review.reject(operator, id, "NOPE", " "))).containsExactly("code", "text");
        review.reject(operator, id, "FALSE_PRICE", "근거 없는 최저가 표현");
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.REJECTED);
        assertThat(a.rejectText()).isEqualTo("근거 없는 최저가 표현");
        assertThat(codeOf(() -> review.reject(operator, id, "OTHER", "다시"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> approve(id, "ONLINE", freshPeriod(3)))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'BIZ_APPLICATION' AND payload::text LIKE '%REJECTED%'",
                Integer.class, user)).isEqualTo(1);
    }

    @Test
    void 이미_승인한_건을_다시_승인하면_409_BIZ_APPLICATION_STATE_CHANGED() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        BizPeriods.Period p = freshPeriod(3);
        approve(id, "ONLINE", p);
        assertThat(codeOf(() -> approve(id, "MANUAL", p))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 수동_전환은_결제_대기만_열린_결제를_닫고_입금_대기로() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        approve(id, "ONLINE", freshPeriod(3));
        var checkout = members.startPayment(user, id);
        String deadline = clock.instant().plus(Duration.ofDays(7)).toString();
        assertThat(fieldsOf(() -> review.switchToManual(operator, id, deadline, ""))).containsExactly("reason");
        review.switchToManual(operator, id, deadline, "계좌이체 요청");
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        assertThat(a.paymentMethod()).isEqualTo(BizPaymentMethod.MANUAL);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE payment_id = ?", String.class, checkout.paymentId())).isEqualTo("EXPIRED");
        assertThat(codeOf(() -> review.switchToManual(operator, id, deadline, "다시"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        Map<String, Object> audit = lastAudit("BIZ_APPLICATION", Long.toString(id), "BIZ_APP_MANUAL_SWITCH");
        assertThat(audit).isNotNull();
    }
}

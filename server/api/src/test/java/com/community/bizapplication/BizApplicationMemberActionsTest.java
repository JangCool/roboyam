package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.request.BizCreativeRequest;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizSlotService;
import com.community.payment.dto.PaymentCheckout;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 취소(심사·결제·입금 대기 — 입금 기록 뒤 거부) · 소재 바꾸기(심사 대기는 새 버전 · 승인 뒤 결제 전은 다시 심사 대기 + 자리 반납 + 열린 결제 닫기 · 그 밖 409).
 */
class BizApplicationMemberActionsTest extends BizApplicationApiTestBase {

    @Autowired
    private BizSlotService slots;

    @Test
    void 심사_대기_결제_대기_입금_대기는_사업자가_바로_취소하고_결제_대기의_열린_결제는_닫힌다() {
        long user = approvedBusiness();
        long submitted = submitTextLine(user, 0, 3, "ONLINE");
        members.cancel(user, submitted);
        assertThat(app(submitted).status()).isEqualTo(BizApplicationStatus.CANCELED);

        long awaitingPayment = submitTextLine(user, 0, 3, "ONLINE");
        approve(awaitingPayment, "ONLINE", freshPeriod(3));
        PaymentCheckout checkout = members.startPayment(user, awaitingPayment);
        members.cancel(user, awaitingPayment);
        assertThat(app(awaitingPayment).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE payment_id = ?", String.class, checkout.paymentId())).isEqualTo("EXPIRED");

        long awaitingDeposit = submitTextLine(user, 0, 3, "MANUAL");
        approve(awaitingDeposit, "MANUAL", freshPeriod(3));
        members.cancel(user, awaitingDeposit);
        assertThat(app(awaitingDeposit).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.exists(awaitingDeposit)).isFalse(); // 돈이 오가지 않았다
        assertThat(codeOf(() -> members.cancel(user, awaitingDeposit))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 입금_기록이_있는_입금_대기와_예약은_사업자가_취소할_수_없다_409() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "MANUAL");
        approve(id, "MANUAL", freshPeriod(3));
        review.saveDeposit(operator, id, app(id).approved().total(), today().toString(), "시험상사", null);
        assertThat(codeOf(() -> members.cancel(user, id))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> members.changeCreative(user, id, new BizCreativeRequest(null, "https://a.example.com", null, "새 문구", null, null))))
                .isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        long scheduled = submitTextLine(user, 0, 3, "ONLINE");
        approve(scheduled, "ONLINE", freshPeriod(3));
        pay(user, scheduled);
        assertThat(app(scheduled).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(codeOf(() -> members.cancel(user, scheduled))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 심사_대기_중_소재를_바꾸면_새_버전만_쌓이고_상태는_그대로() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        members.changeCreative(user, id, new BizCreativeRequest(null, "https://a.example.com/new", null, "새 한 줄", null, null));
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.title()).isEqualTo("새 한 줄");
        assertThat(store.latestCreative(id).orElseThrow().version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_creative_versions WHERE application_id = ?", Integer.class, id)).isEqualTo(2); // 옛 버전 보존
        assertThat(fieldsOf(() -> members.changeCreative(user, id, new BizCreativeRequest(null, "nope", null, "x".repeat(41), null, null))))
                .containsExactly("text", "linkUrl");
    }

    @Test
    void 승인_뒤_결제_전에_소재를_바꾸면_다시_심사_대기_승인_스냅샷_비움_자리_반납_열린_결제_닫기() {
        long user = approvedBusiness();
        long id = submitBanner(user, "WIDE", 0, 2, "ONLINE");
        BizPeriods.Period p = freshPeriod(2);
        approve(id, "ONLINE", p);
        assertThat(slots.room(BizProduct.WIDE_BANNER, p.start(), p.end(), null).remaining()).isEqualTo(1);
        PaymentCheckout checkout = members.startPayment(user, id);
        members.changeCreative(user, id, new BizCreativeRequest(Long.toString(adImage(user)), "https://shop.example.com/new", "새 배너", null, null, null));
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.approved()).isNull();
        assertThat(a.paymentMethod()).isNull();
        assertThat(a.paymentDeadlineAt()).isNull();
        assertThat(slots.room(BizProduct.WIDE_BANNER, p.start(), p.end(), null).remaining()).isEqualTo(2); // 자리 반납
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE payment_id = ?", String.class, checkout.paymentId())).isEqualTo("EXPIRED");
        assertThat(store.latestCreative(id).orElseThrow().approvedAt()).isNull(); // 새 버전은 아직 승인 전
        // 닫힌 결제에 늦게 돈이 와도 확정되지 않는다(늦은 결제 → 자동 취소)
        fakePg.pay(checkout.paymentId(), checkout.amount(), "KRW", clock.instant());
        confirm.confirm(checkout.paymentId(), com.community.payment.dto.ConfirmedVia.WEBHOOK);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(jdbc.queryForObject("SELECT anomaly FROM payment_orders WHERE payment_id = ?", String.class, checkout.paymentId())).isEqualTo("LATE_PAYMENT");
    }

    @Test
    void 예약_반려_취소_건의_소재는_사업자가_바꿀_수_없다_409() {
        long user = approvedBusiness();
        long rejected = submitTextLine(user, 0, 3, "ONLINE");
        review.reject(operator, rejected, "OTHER", "다시 신청해 주세요");
        assertThat(codeOf(() -> members.changeCreative(user, rejected, new BizCreativeRequest(null, "https://a.example.com", null, "새", null, null))))
                .isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        long scheduled = submitTextLine(user, 0, 3, "ONLINE");
        approve(scheduled, "ONLINE", freshPeriod(3));
        pay(user, scheduled);
        assertThat(codeOf(() -> members.changeCreative(user, scheduled, new BizCreativeRequest(null, "https://a.example.com", null, "새", null, null))))
                .isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 핫딜_소재_바꾸기는_노출_자리를_바꿀_수_없다() {
        long user = approvedBusiness();
        long id = submitDeal(user, "NONE", null, null, "ONLINE");
        assertThat(fieldsOf(() -> members.changeCreative(user, id, new BizCreativeRequest(null, null, null, null, "새 제목", deal("HOME_TOP")))))
                .containsExactly("deal.placement");
        members.changeCreative(user, id, new BizCreativeRequest(null, null, null, null, "새 제목", deal("NONE")));
        assertThat(app(id).title()).isEqualTo("새 제목");
    }

    @Test
    void 결제_기한이_1분도_안_남았으면_결제를_시작할_수_없다_열린_결제는_다시_준다() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        approve(id, "ONLINE", freshPeriod(3));
        PaymentCheckout first = members.startPayment(user, id);
        PaymentCheckout again = members.startPayment(user, id);
        assertThat(again.paymentId()).isEqualTo(first.paymentId()); // 1분 이상 남은 열린 결제는 그대로
        assertThat(first.amount()).isEqualTo(app(id).approved().total());
        assertThat(java.time.Duration.between(clock.instant(), first.expiresAt())).isLessThanOrEqualTo(Duration.ofMinutes(30));
        clock.set(app(id).paymentDeadlineAt().minusSeconds(30));
        assertThat(codeOf(() -> members.startPayment(user, id))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        clock.set(app(id).paymentDeadlineAt().plusSeconds(1));
        assertThat(codeOf(() -> members.startPayment(user, id))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }
}

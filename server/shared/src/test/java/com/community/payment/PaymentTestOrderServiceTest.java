package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.dto.response.PaymentResult;
import com.community.payment.exception.FakePgAlreadyDoneException;
import com.community.payment.exception.PaymentGatewayNotFakeException;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentStatus;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import com.community.payment.testorder.PaymentTestOrderService;
import com.community.payment.testorder.PaymentTestViews.CreatedView;
import com.community.payment.testorder.PaymentTestViews.FakeOutcome;
import com.community.payment.testorder.PaymentTestViews.StatusView;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 결제 시험 주문(계약 결정 7)의 규칙 — 2a-1 화면 계약에 맞춘 모양(「2a-4 준비 — 회원 API 를 화면 계약에 맞춤」).
 *
 * <ul>
 *   <li>만들기: 금액 100~1,000,000원·유효 시간 1~60분 밖이면 {@code fields.amount}·{@code fields.validMinutes}(둘 다 틀리면 둘 다), 유효 시각 = 만든 시각 + {@code validMinutes}분,
 *       결제창 정보는 공용 모양 {@code checkout}</li>
 *   <li>가짜 결제창: {@code late=true} → 결제 시각 = 유효 시각 + 1분 → 늦은 결제(결정 4) + 전액 자동 취소, 결과를 이미 남긴 결제 409 {@code FAKE_PG_ALREADY_DONE},
 *       가짜 게이트웨이가 아니면 409 {@code PAYMENT_GATEWAY_NOT_FAKE}, 남의 결제 404</li>
 *   <li>다시 읽기: 완료 API 와 같은 {@link PaymentResult}(+ 처리기 현금·취소 줄)</li>
 * </ul>
 */
class PaymentTestOrderServiceTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    /** 가짜 게이트웨이(결제 상태는 시험 키트의 가짜 PG — 완료 API 가 보는 것과 같은 표)를 쓰는 시험 주문 서비스 */
    private static PaymentTestOrderService service() {
        return service(kit.gateway.fake);
    }

    private static PaymentTestOrderService service(PaymentGateway gateway) {
        return new PaymentTestOrderService(kit.orders, kit.orderStore, kit.cancelStore, kit.handler, gateway, kit.ids, kit.spring.getBeanProvider(Clock.class));
    }

    private static CreatedView create(PaymentTestOrderService service, long member, Long amount, Integer validMinutes) {
        return kit.tx.execute(status -> service.create(member, amount, validMinutes, null));
    }

    private static PaymentResult complete(long member, CreatedView created) {
        return kit.completion.complete(member, created.checkout().paymentId(), PaymentOrderType.PAYMENT_TEST, Long.parseLong(created.orderRefId()));
    }

    @Test
    void 만들기는_금액과_유효_시간의_범위를_함께_검사한다() {
        PaymentTestOrderService service = service();
        long member = kit.newMember();
        assertThatThrownBy(() -> create(service, member, 5_000L, 0)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("validMinutes"));
        assertThatThrownBy(() -> create(service, member, 5_000L, 61)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("validMinutes"));
        assertThatThrownBy(() -> create(service, member, 5_000L, null)).as("유효 시간 없음").isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("validMinutes"));
        assertThatThrownBy(() -> create(service, member, 99L, 10)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("amount"));
        assertThatThrownBy(() -> create(service, member, 1_000_001L, 10)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("amount"));
        assertThatThrownBy(() -> create(service, member, null, 99)).as("둘 다 틀리면 둘 다").isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields().keySet()).containsExactly("amount", "validMinutes"));
        assertThat(kit.count("SELECT count(*) FROM payment_orders WHERE user_id = ?", member)).as("하나도 만들지 않았다").isZero();

        assertThat(create(service, member, 100L, 1).checkout().amount()).as("하한").isEqualTo(100);
        assertThat(create(service, member, 1_000_000L, 60).checkout().amount()).as("상한").isEqualTo(1_000_000);
    }

    @Test
    void 유효_시각은_만든_시각_더하기_validMinutes_분이고_결제창_정보는_공용_모양이다() {
        long member = kit.newMember();
        for (int minutes : new int[] {1, 7, 60}) {
            CreatedView created = create(service(), member, 12_000L, minutes);
            OrderRow row = kit.order(created.checkout().paymentId());
            assertThat(Duration.between(row.createdAt(), row.expiresAt())).as(minutes + "분").isEqualTo(Duration.ofMinutes(minutes));
            assertThat(created.expiresAt()).isEqualTo(row.expiresAt());
            assertThat(created.orderType()).isEqualTo("PAYMENT_TEST");
            assertThat(created.orderRefId()).isEqualTo(Long.toString(row.orderRefId()));
            assertThat(created.checkout().gateway()).isEqualTo(PaymentGatewayKind.FAKE);
            assertThat(created.checkout().paymentId()).matches("pay_[A-Za-z0-9]{32}");
            assertThat(created.checkout().orderName()).isEqualTo(PaymentTestOrderService.ORDER_NAME);
            assertThat(created.checkout().amount()).isEqualTo(12_000);
            assertThat(created.checkout().currency()).isEqualTo("KRW");
            assertThat(created.checkout().storeId()).isNull();
            assertThat(created.checkout().channelKey()).isNull();
        }
    }

    @Test
    void 늦게_결제하면_결제_시각이_유효_시각_1분_뒤이고_늦은_결제로_전액_자동_취소된다() {
        PaymentTestOrderService service = service();
        long member = kit.newMember();
        CreatedView created = create(service, member, 8_000L, 5);
        String paymentId = created.checkout().paymentId();

        service.fakeCheckout(member, paymentId, FakeOutcome.PAID, 8_000L, true);
        PgPayment pg = kit.gateway.fake.getPayment(paymentId);
        assertThat(pg.paidAt()).isEqualTo(created.expiresAt().plus(Duration.ofMinutes(1)));

        PaymentResult result = complete(member, created);
        assertThat(result.anomaly()).isEqualTo(PaymentAnomaly.LATE_PAYMENT);
        assertThat(result.accepted()).isFalse();
        assertThat(result.paidAt()).isEqualTo(pg.paidAt());
        assertThat(result.paidAmount()).isEqualTo(8_000);
        // 커밋 뒤 즉시 취소가 끝났다 — 자동 취소 줄·누계·상태가 응답에 보인다
        assertThat(result.cancel()).isEqualTo(new PaymentResult.Cancel(8_000, CancelStatus.SUCCEEDED));
        assertThat(result.canceledAmount()).isEqualTo(8_000);
        assertThat(result.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(kit.handler.cashOf(Long.parseLong(created.orderRefId()))).as("처리기를 부르지 않았다").isZero();
    }

    @Test
    void 제때_결제하면_받아들이고_자동_취소가_없다() {
        PaymentTestOrderService service = service();
        long member = kit.newMember();
        CreatedView created = create(service, member, 8_000L, 5);
        service.fakeCheckout(member, created.checkout().paymentId(), FakeOutcome.PAID, null, false);

        PaymentResult result = complete(member, created);
        assertThat(result.anomaly()).isNull();
        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.paidAmount()).as("금액이 없으면 주문 금액").isEqualTo(8_000);
        assertThat(result.paidAt()).isEqualTo(kit.now());
        assertThat(result.canceledAmount()).isZero();
        assertThat(result.cancel()).isNull();
    }

    @Test
    void 금액을_바꿔_결제하면_금액_불일치이고_1원_미만은_400() {
        PaymentTestOrderService service = service();
        long member = kit.newMember();
        CreatedView created = create(service, member, 8_000L, 5);
        String paymentId = created.checkout().paymentId();
        assertThatThrownBy(() -> service.fakeCheckout(member, paymentId, FakeOutcome.PAID, 0L, false)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("amount"));
        assertThat(kit.count("SELECT count(*) FROM fake_pg_payments WHERE payment_id = ?", paymentId)).isZero();

        service.fakeCheckout(member, paymentId, FakeOutcome.PAID, 4_000L, false);
        PaymentResult result = complete(member, created);
        assertThat(result.anomaly()).isEqualTo(PaymentAnomaly.AMOUNT_MISMATCH);
        assertThat(result.accepted()).isFalse();
        assertThat(result.paidAmount()).isEqualTo(4_000);
        assertThat(result.cancel()).isEqualTo(new PaymentResult.Cancel(4_000, CancelStatus.SUCCEEDED));
    }

    @Test
    void 이미_결과를_남긴_결제는_409_FAKE_PG_ALREADY_DONE_이고_처음_결과가_남는다() {
        PaymentTestOrderService service = service();
        long member = kit.newMember();
        String paid = create(service, member, 8_000L, 5).checkout().paymentId();
        service.fakeCheckout(member, paid, FakeOutcome.PAID, 8_000L, false);
        assertThatThrownBy(() -> service.fakeCheckout(member, paid, FakeOutcome.PAID, 1_000L, true)).isInstanceOf(FakePgAlreadyDoneException.class)
                .satisfies(e -> {
                    assertThat(((CommonException) e).getStatus().value()).isEqualTo(409);
                    assertThat(((CommonException) e).getErrorCode()).isEqualTo("FAKE_PG_ALREADY_DONE");
                });
        assertThatThrownBy(() -> service.fakeCheckout(member, paid, FakeOutcome.FAILED, null, false)).isInstanceOf(FakePgAlreadyDoneException.class);
        assertThat(kit.gateway.fake.getPayment(paid).totalAmount()).as("처음 결과 그대로").isEqualTo(8_000);
        assertThat(kit.gateway.fake.getPayment(paid).status()).isEqualTo(PgPaymentStatus.PAID);

        String failed = create(service, member, 8_000L, 5).checkout().paymentId();
        service.fakeCheckout(member, failed, FakeOutcome.FAILED, null, false);
        assertThat(kit.gateway.fake.getPayment(failed).status()).isEqualTo(PgPaymentStatus.FAILED);
        assertThatThrownBy(() -> service.fakeCheckout(member, failed, FakeOutcome.PAID, 8_000L, false)).isInstanceOf(FakePgAlreadyDoneException.class);
        assertThat(kit.gateway.fake.getPayment(failed).status()).isEqualTo(PgPaymentStatus.FAILED);
    }

    @Test
    void 가짜_게이트웨이가_아니면_409_PAYMENT_GATEWAY_NOT_FAKE_이고_남의_결제는_404() {
        long member = kit.newMember();
        // 시험 키트의 게이트웨이(가짜 PG 를 감싼 대역)는 가짜 게이트웨이가 아니다 — 포트원 서버처럼 다룬다
        PaymentTestOrderService notFake = service(kit.gateway);
        String paymentId = create(notFake, member, 8_000L, 5).checkout().paymentId();
        assertThatThrownBy(() -> notFake.fakeCheckout(member, paymentId, FakeOutcome.PAID, 8_000L, false)).isInstanceOf(PaymentGatewayNotFakeException.class)
                .satisfies(e -> {
                    assertThat(((CommonException) e).getStatus().value()).isEqualTo(409);
                    assertThat(((CommonException) e).getErrorCode()).isEqualTo("PAYMENT_GATEWAY_NOT_FAKE");
                });
        assertThat(kit.count("SELECT count(*) FROM fake_pg_payments WHERE payment_id = ?", paymentId)).isZero();

        assertThatThrownBy(() -> service().fakeCheckout(kit.newMember(), paymentId, FakeOutcome.PAID, 8_000L, false)).isInstanceOf(PaymentOrderNotFoundException.class);
        assertThatThrownBy(() -> service().status(kit.newMember(), paymentId)).isInstanceOf(PaymentOrderNotFoundException.class);
        assertThat(kit.count("SELECT count(*) FROM fake_pg_payments WHERE payment_id = ?", paymentId)).isZero();
    }

    @Test
    void 새_오류와_칸_문구는_두_언어에_같은_자리표시자로_있다() throws java.io.IOException {
        java.util.Properties ko = bundle("messages/messages_ko.properties");
        java.util.Properties en = bundle("messages/messages_en.properties");
        for (String key : java.util.List.of("error.PAYMENT_GATEWAY_NOT_FAKE", "error.FAKE_PG_ALREADY_DONE", "payment.test.validMinutes.range", "payment.test.fakeAmount.min")) {
            assertThat(ko.getProperty(key)).as("ko " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
            assertThat(placeholders(ko.getProperty(key))).as(key).isEqualTo(placeholders(en.getProperty(key)));
        }
    }

    private static java.util.Properties bundle(String path) throws java.io.IOException {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.Reader reader = new java.io.InputStreamReader(PaymentTestOrderServiceTest.class.getClassLoader().getResourceAsStream(path),
                java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static java.util.Set<String> placeholders(String text) {
        java.util.Set<String> found = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\d+}").matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    @Test
    void 다시_읽기는_완료_API_와_같은_결과에_처리기_현금과_취소_줄을_더한다() {
        PaymentTestOrderService service = service();
        long member = kit.newMember();
        CreatedView created = kit.tx.execute(status -> service.create(member, 9_000L, 5, Decision.REJECT));
        String paymentId = created.checkout().paymentId();

        StatusView ready = service.status(member, paymentId);
        assertThat(ready.result()).isEqualTo(new PaymentResult(paymentId, "PAYMENT_TEST", created.orderRefId(), PaymentStatus.READY, null, false, 9_000, null, 0, null,
                null));
        assertThat(ready.cancels()).isEmpty();

        kit.gateway.script(TestGateway.CancelScript.DOWN); // 즉시 취소가 실패해 PENDING 으로 남는다
        service.fakeCheckout(member, paymentId, FakeOutcome.PAID, null, false);
        PaymentResult completed = complete(member, created);
        assertThat(completed.anomaly()).isEqualTo(PaymentAnomaly.DOMAIN_REJECTED);
        assertThat(completed.accepted()).isFalse();
        assertThat(completed.cancel()).isEqualTo(new PaymentResult.Cancel(9_000, CancelStatus.PENDING));
        assertThat(completed.canceledAmount()).as("포트원 취소가 끝나기 전").isZero();
        assertThat(completed.status()).isEqualTo(PaymentStatus.PAID);

        StatusView status = service.status(member, paymentId);
        assertThat(status.result()).isEqualTo(completed);
        assertThat(status.handlerCash()).isZero();
        assertThat(status.cancels()).singleElement().satisfies(c -> {
            assertThat(c.status()).isEqualTo(CancelStatus.PENDING);
            assertThat(c.attempts()).isEqualTo(1);
        });
    }
}

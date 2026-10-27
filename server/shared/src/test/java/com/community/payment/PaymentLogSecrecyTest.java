package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.gateway.portone.PortOnePaymentGateway;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.service.PaymentWebhookService.WebhookRequest;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import com.community.portone.dto.PortOnePaymentCredentials;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 로그·기록에 비밀값이 없다(계약 「규칙 → 테스트」, {@code _backend-core.md} 「비밀값」) — 포트원 API 비밀키·웹훅 비밀키·웹훅 서명·응답 본문(구매자 정보)·예외 문구의 이메일은
 * 로그에도, 취소 대기열의 마지막 오류에도 남지 않는다. 남는 것은 HTTP 상태·포트원 오류 type·예외 클래스 이름·가린 한 줄뿐.
 */
@ExtendWith(OutputCaptureExtension.class)
class PaymentLogSecrecyTest {

    private static final String SECRET = PortOnePaymentGatewayTest.SECRET;
    private static final String WEBHOOK_SECRET = PortOnePaymentGatewayTest.WEBHOOK_SECRET;

    @Test
    void 포트원_오류_응답의_본문과_비밀키는_로그에_없다(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PortOnePaymentGateway gateway = new PortOnePaymentGateway(
                () -> Optional.of(new PortOnePaymentCredentials(PortOnePaymentGatewayTest.STORE, SECRET, "channel", WEBHOOK_SECRET)), builder);
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("{\"type\":\"UNAUTHORIZED\",\"message\":\"bad key " + SECRET + " for buyer kim@example.com\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.getPayment(PortOnePaymentGatewayTest.PAY)).isInstanceOf(PaymentGatewayUnavailableException.class);
        server.reset();
        server.expect(anything()).andRespond(withStatus(HttpStatus.CONFLICT)
                .body("{\"type\":\"PAYMENT_ALREADY_CANCELLED\",\"message\":\"kim@example.com\"}").contentType(MediaType.APPLICATION_JSON));
        gateway.cancel(PortOnePaymentGatewayTest.PAY, 1, 1, "r");
        server.reset();
        server.expect(anything()).andRespond(withStatus(HttpStatus.OK).body("{broken " + SECRET).contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.getPayment(PortOnePaymentGatewayTest.PAY)).isInstanceOf(PaymentGatewayUnavailableException.class);

        assertThat(output.getAll()).contains("UNAUTHORIZED").contains("PAYMENT_ALREADY_CANCELLED")
                .doesNotContain(SECRET).doesNotContain(WEBHOOK_SECRET).doesNotContain("kim@example.com");
        assertThat(new PortOnePaymentCredentials("s", SECRET, "c", WEBHOOK_SECRET).toString()).doesNotContain(SECRET).doesNotContain(WEBHOOK_SECRET);
    }

    @Test
    void 거절한_웹훅의_서명과_본문은_로그_기록에_없다(CapturedOutput output) {
        PaymentTestKit kit = PaymentTestKit.get();
        kit.newEpoch();
        String body = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"pay_secretbuyer\",\"customer\":\"kim@example.com\"}}";
        String forged = "v1,Zm9yZ2VkLXNpZ25hdHVyZS12YWx1ZS1tdXN0LW5vdC1sZWFr";
        kit.webhooks.handle(new WebhookRequest("msg_forged_123", Long.toString(kit.now().getEpochSecond()), forged, body.getBytes(StandardCharsets.UTF_8)));
        assertThat(output.getAll()).doesNotContain(forged).doesNotContain("kim@example.com").doesNotContain("pay_secretbuyer");
        assertThat(kit.count("SELECT count(*) FROM payment_webhook_logs WHERE webhook_id = 'msg_forged_123' OR payment_id = 'pay_secretbuyer'"))
                .as("검증 안 된 머리글·본문 값은 기록에도 없다").isZero();
    }

    @Test
    void 취소_실행의_예외_문구는_가려서_남고_로그에는_클래스_이름만(CapturedOutput output) {
        PaymentTestKit kit = PaymentTestKit.get();
        kit.newEpoch();
        PaymentTestKit.Created created = kit.create(kit.newMember(), 5_000, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), 5_000);
        kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);
        kit.gateway.script(TestGateway.CancelScript.BOOM); // 뜻밖의 예외 — 문구에 이메일이 섞였다
        long failing = kit.tx.execute(status -> kit.cancels.request(new com.community.payment.dto.PaymentCancelOrder(created.orderId(), 1_000,
                com.community.payment.dto.CancelKind.ADMIN, "운영자 취소", "LOG:" + kit.ids.nextId(), null)));
        CancelRow failed = kit.cancelStore.findById(failing).orElseThrow();
        assertThat(failed.lastError()).contains("IllegalStateException").doesNotContain("kim@example.com");
        assertThat(kit.gateway.cancelReasons).as("포트원에 보내는 사유에 내부 사유를 싣지 않는다").allSatisfy(reason -> assertThat(reason).doesNotContain("운영자"));

        kit.gateway.script(TestGateway.CancelScript.DOWN);
        long down = kit.tx.execute(status -> kit.cancels.request(new com.community.payment.dto.PaymentCancelOrder(created.orderId(), 1_000,
                com.community.payment.dto.CancelKind.ADMIN, "운영자 취소", "LOG:" + kit.ids.nextId(), null)));
        assertThat(kit.cancelStore.findById(down).orElseThrow().lastError()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        assertThat(output.getAll()).doesNotContain("kim@example.com");
    }
}

package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.payment.gateway.WebhookVerification;
import com.community.payment.gateway.portone.StandardWebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Standard Webhooks 서명 규칙(계약 「웹훅 처리 표」의 서명 줄, commerce.md 8-4·P23) — 서명 대상 {@code {id}.{timestamp}.{본문}}, 비밀키 {@code whsec_} 뒤 base64,
 * HMAC-SHA256, {@code v1,<서명>} 여럿 중 하나라도 맞으면 통과, 시각 오차 5분. 규격 문서의 시험 값(Standard Webhooks 참조 구현의 예)으로 확인한다.
 */
class StandardWebhookVerifierTest {

    // Standard Webhooks 참조 구현의 시험 값
    private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final String ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final String TIMESTAMP = "1614265330";
    private static final byte[] BODY = "{\"test\": 2432232314}".getBytes(StandardCharsets.UTF_8);
    private static final String SIGNATURE = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";
    private static final Instant AT = Instant.ofEpochSecond(1614265330);

    @Test
    void 규격의_시험_값이_맞는다() {
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.OK);
        assertThat(StandardWebhookVerifier.signature(SECRET, ID, TIMESTAMP, BODY)).isEqualTo(SIGNATURE);
    }

    @Test
    void 여러_서명_중_하나만_맞아도_통과하고_v1_이_아닌_것은_건너뛴다() {
        String header = "v1,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= v2," + SIGNATURE.substring(3) + " " + SIGNATURE;
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, header, BODY, AT)).isEqualTo(WebhookVerification.OK);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, "v2," + SIGNATURE.substring(3), BODY, AT)).isEqualTo(WebhookVerification.BAD_SIGNATURE);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, "v1,not-base64!!", BODY, AT)).isEqualTo(WebhookVerification.BAD_SIGNATURE);
    }

    @Test
    void 본문_ID_시각_비밀키_중_하나라도_다르면_틀린_서명이다() {
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, SIGNATURE, "{\"test\": 2432232315}".getBytes(StandardCharsets.UTF_8), AT))
                .isEqualTo(WebhookVerification.BAD_SIGNATURE);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID + "x", TIMESTAMP, SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.BAD_SIGNATURE);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, "1614265331", SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.BAD_SIGNATURE);
        assertThat(StandardWebhookVerifier.verify("whsec_" + "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo=", ID, TIMESTAMP, SIGNATURE, BODY, AT))
                .isEqualTo(WebhookVerification.BAD_SIGNATURE);
    }

    @Test
    void whsec_머리는_있어도_없어도_같은_키다() {
        assertThat(StandardWebhookVerifier.verify(SECRET.substring("whsec_".length()), ID, TIMESTAMP, SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.OK);
    }

    @Test
    void 시각_오차는_5분까지() {
        assertThat(StandardWebhookVerifier.TOLERANCE).isEqualTo(Duration.ofMinutes(5));
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, SIGNATURE, BODY, AT.plus(Duration.ofMinutes(5)))).isEqualTo(WebhookVerification.OK);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, SIGNATURE, BODY, AT.minus(Duration.ofMinutes(5)))).isEqualTo(WebhookVerification.OK);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, SIGNATURE, BODY, AT.plus(Duration.ofMinutes(5)).plusSeconds(1)))
                .isEqualTo(WebhookVerification.TIMESTAMP_SKEW);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, SIGNATURE, BODY, AT.minus(Duration.ofMinutes(5)).minusSeconds(1)))
                .isEqualTo(WebhookVerification.TIMESTAMP_SKEW);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, "16142653x0", SIGNATURE, BODY, AT)).as("숫자가 아님").isEqualTo(WebhookVerification.TIMESTAMP_SKEW);
    }

    @Test
    void 머리글이_빠지면_MISSING_HEADERS_비밀키가_없거나_틀리면_NOT_CONFIGURED() {
        assertThat(StandardWebhookVerifier.verify(SECRET, null, TIMESTAMP, SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.MISSING_HEADERS);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, " ", SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.MISSING_HEADERS);
        assertThat(StandardWebhookVerifier.verify(SECRET, ID, TIMESTAMP, "", BODY, AT)).isEqualTo(WebhookVerification.MISSING_HEADERS);
        assertThat(StandardWebhookVerifier.verify(null, ID, TIMESTAMP, SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.NOT_CONFIGURED);
        assertThat(StandardWebhookVerifier.verify("whsec_###", ID, TIMESTAMP, SIGNATURE, BODY, AT)).isEqualTo(WebhookVerification.NOT_CONFIGURED);
    }
}

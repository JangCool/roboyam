package com.community.payment.gateway.portone;

import com.community.payment.gateway.WebhookVerification;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Standard Webhooks 서명 검증(포트원 V2 웹훅 2024-04-25 — 계약 「웹훅 처리 표」, commerce.md 8-4).
 *
 * <ul>
 *   <li>머리글 {@code webhook-id}·{@code webhook-timestamp}(유닉스 초)·{@code webhook-signature} 가 모두 있어야 한다.</li>
 *   <li>시각이 지금과 {@link #TOLERANCE}(5분, 코드 상수 — 결정 18) 넘게 차이 나면 거절(재전송 공격 막기).</li>
 *   <li>서명 대상 = {@code {webhook-id}.{webhook-timestamp}.{본문 원본}}, 키 = 비밀키에서 {@code whsec_} 를 뗀 나머지를 base64 로 푼 바이트, HMAC-SHA256 → base64.</li>
 *   <li>{@code webhook-signature} 는 공백으로 나뉜 {@code v1,<서명>} 여럿 — 하나라도 맞으면 통과(비밀키를 바꾸는 동안 둘을 받는다). 비교는 상수 시간({@link MessageDigest#isEqual}).</li>
 * </ul>
 * 비밀키·서명·본문은 로그에 남기지 않는다.
 */
public final class StandardWebhookVerifier {

    /** 시각 허용 오차(결정 18 — 코드 상수) */
    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String SECRET_PREFIX = "whsec_";

    private StandardWebhookVerifier() {
    }

    public static WebhookVerification verify(String secret, String webhookId, String webhookTimestamp, String webhookSignature, byte[] body, Instant now) {
        byte[] key = key(secret);
        if (key == null) {
            return WebhookVerification.NOT_CONFIGURED; // 검증할 수 없다 — 머리글을 보기 전에 알린다(설정한 뒤 재전송을 받는다)
        }
        if (blank(webhookId) || blank(webhookTimestamp) || blank(webhookSignature) || body == null) {
            return WebhookVerification.MISSING_HEADERS;
        }
        long seconds;
        try {
            seconds = Long.parseLong(webhookTimestamp.strip());
        } catch (NumberFormatException e) {
            return WebhookVerification.TIMESTAMP_SKEW;
        }
        long skew = Math.abs(now.getEpochSecond() - seconds);
        if (skew > TOLERANCE.toSeconds()) {
            return WebhookVerification.TIMESTAMP_SKEW;
        }
        byte[] expected = sign(key, webhookId, webhookTimestamp.strip(), body);
        for (String part : webhookSignature.strip().split(" +")) {
            int comma = part.indexOf(',');
            if (comma < 0 || !"v1".equals(part.substring(0, comma))) {
                continue;
            }
            byte[] given;
            try {
                given = Base64.getDecoder().decode(part.substring(comma + 1));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, given)) {
                return WebhookVerification.OK;
            }
        }
        return WebhookVerification.BAD_SIGNATURE;
    }

    /**
     * 서명을 만든다(시험·가짜 웹훅 흉내용) — {@code v1,<base64>}.
     *
     * @throws IllegalArgumentException 비밀키 형식이 틀렸을 때
     */
    public static String signature(String secret, String webhookId, String webhookTimestamp, byte[] body) {
        byte[] key = key(secret);
        if (key == null) {
            throw new IllegalArgumentException("웹훅 비밀키 형식이 틀렸다");
        }
        return "v1," + Base64.getEncoder().encodeToString(sign(key, webhookId, webhookTimestamp, body));
    }

    /** 비밀키 → HMAC 키 바이트. 비었거나 base64 가 아니면 null */
    static byte[] key(String secret) {
        if (blank(secret)) {
            return null;
        }
        String encoded = secret.strip();
        if (encoded.startsWith(SECRET_PREFIX)) {
            encoded = encoded.substring(SECRET_PREFIX.length());
        }
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            return key.length == 0 ? null : key;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] sign(byte[] key, String webhookId, String webhookTimestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((webhookId + "." + webhookTimestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 을 쓸 수 없다", e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

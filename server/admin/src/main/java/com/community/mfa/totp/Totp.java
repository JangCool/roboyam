package com.community.mfa.totp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP(RFC 6238) — HMAC-SHA1, 6자리, 30초 칸, 앞뒤 1칸 허용. 외부 라이브러리 없이 직접 구현한다(docs/features/commerce-foundation-contract.md 「0-3」).
 *
 * <ul>
 *   <li>시간 칸(step) = 유닉스 초 ÷ 30(내림). 코드 = HOTP(비밀키, 칸)(RFC 4226 — 8바이트 big-endian 칸의 HMAC-SHA1 → 동적 절단 → 10^자릿수 나머지, 앞을 0 으로 채움).</li>
 *   <li>확인은 지금 칸과 앞뒤 1칸만 본다(시계가 30초 어긋나도 통과, 2칸은 거절). <b>마지막으로 받아들인 칸 이하의 코드는 거절</b>한다 — 같은 코드 재사용·옛 코드 방지.
 *       맞는 칸이 여럿이면(우연히 같은 코드) 받아들일 수 있는 가장 늦은 칸을 고른다.</li>
 *   <li>코드 비교는 길이·내용에 상관없이 같은 시간이 걸리게 한다({@link MessageDigest#isEqual}).</li>
 * </ul>
 * 시각은 부르는 쪽이 준다(시험이 시계를 정할 수 있게). 비밀키·코드를 로그·오류 문구에 싣지 않는다. 상태가 없는 순수 함수다.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** 앞뒤로 허용하는 칸 수 */
    public static final int WINDOW = 1;

    private Totp() {
    }

    /** 확인 결과 */
    public enum Result {
        /** 받아들였다 — {@link Match#step()} 을 마지막 칸으로 저장한다 */
        ACCEPTED,
        /** 맞는 코드지만 이미 받아들인 칸(또는 그 이전 칸)이다 */
        REUSED,
        /** 맞지 않는다 */
        INVALID
    }

    /** @param step 받아들인 칸(ACCEPTED 일 때만 뜻이 있다) */
    public record Match(Result result, long step) {
    }

    /** 그 시각의 시간 칸 */
    public static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), PERIOD_SECONDS);
    }

    /** 6자리 코드 */
    public static String code(byte[] secret, long step) {
        return code(secret, step, DIGITS);
    }

    /** {@code digits} 자리 코드(RFC 6238 부록 B 시험 벡터는 8자리다) */
    public static String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            long modulo = 1;
            for (int i = 0; i < digits; i++) {
                modulo *= 10;
            }
            String value = Long.toString(binary % modulo);
            return "0".repeat(digits - value.length()) + value;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 을 쓸 수 없다", e); // 모든 JVM 이 갖는 알고리즘이다
        }
    }

    /**
     * 코드를 확인한다.
     *
     * @param lastStep 마지막으로 받아들인 칸(처음이면 null) — 이 칸 이하는 받아들이지 않는다
     */
    public static Match verify(byte[] secret, String code, Instant now, Long lastStep) {
        if (code == null || code.length() != DIGITS) {
            return new Match(Result.INVALID, -1);
        }
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long current = step(now);
        boolean reused = false;
        for (long candidate = current + WINDOW; candidate >= current - WINDOW; candidate--) {
            boolean equal = MessageDigest.isEqual(given, code(secret, candidate).getBytes(StandardCharsets.US_ASCII));
            if (!equal) {
                continue;
            }
            if (lastStep == null || candidate > lastStep) {
                return new Match(Result.ACCEPTED, candidate);
            }
            reused = true;
        }
        return new Match(reused ? Result.REUSED : Result.INVALID, -1);
    }
}

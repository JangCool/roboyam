package com.community.zconfiguration.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 해시와 무작위 토큰 도우미. 메일 링크에 넣는 1회용 토큰은 원문을 DB 에 두지 않고 SHA-256 해시만 둔다 — 토큰이 DB 에서 새어도 링크를 만들 수 없다.
 * 이메일·토큰 값은 로그에 남기지 않는다.
 */
public final class Digests {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Digests() {
    }

    /** 256비트 무작위 값(URL 에 안전한 base64) — 메일 링크에 들어가는 원문이다 */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 16진 문자열(64자) */
    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e); // 모든 JVM 이 갖는 알고리즘이다
        }
    }
}

package com.community.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 메일 링크에 넣는 무작위 토큰과, DB 에 저장하는 해시(SHA-256 16진 64자). 토큰·이메일 값은 로그에 남기지 않는다 */
final class AuthTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthTokens() {
    }

    /** 256비트 무작위 값(URL 에 안전한 base64) — 메일 링크에 들어가는 원문이다 */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 16진 문자열(64자). 토큰이 DB 에서 새어도 링크를 만들 수 없다 */
    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e); // 모든 JVM 이 갖는 알고리즘이다
        }
    }
}

package com.community.mfa.service;

import com.community.mfa.totp.Base32;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * OTP 비밀키 — 만들기(20바이트 {@link SecureRandom}, base32 로 보인다), 저장용 암호화·복호화({@link SecretEncryptor} = {@code ROBOYAM_CRYPTO_KEY}, 결정 4), otpauth 주소.
 *
 * <ul>
 *   <li>DB 에는 base32 비밀키의 암호문(「v1:」)만 둔다 — 평문은 등록 시작 응답에만 실린다. 키가 없으면 {@code 503 CRYPTO_KEY_MISSING}(앱은 뜬다 — dev·prod 는 이 키 없이 뜨지 않는다).</li>
 *   <li>otpauth 주소: {@code otpauth://totp/<issuer>:<계정>?secret=…&issuer=…&algorithm=SHA1&digits=6&period=30} — 이름 칸은 퍼센트 인코딩(공백은 {@code %20}).</li>
 * </ul>
 * 비밀키를 로그·오류·감사에 싣지 않는다.
 */
@Component
public class MfaSecrets {

    /** 비밀키 길이(바이트) — RFC 4226 권장 160비트 */
    public static final int SECRET_BYTES = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretEncryptor encryptor;

    public MfaSecrets(SecretEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    /** 새 비밀키(base32, 대문자 32자) */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    /** 키가 없으면 {@code 503 CRYPTO_KEY_MISSING} — 비밀키를 만들기 전에 부른다(평문을 보여 주고 저장하지 못하는 일이 없게) */
    public void requireKey() {
        encryptor.requireConfigured();
    }

    public String encrypt(String base32Secret) {
        return encryptor.encrypt(base32Secret);
    }

    /** 암호문 → 비밀키 바이트 */
    public byte[] decrypt(String cipherText) {
        return Base32.decode(encryptor.decrypt(cipherText));
    }

    /** QR 에 넣을 주소 */
    public static String otpauthUri(String issuer, String accountName, String base32Secret) {
        String label = encode(issuer) + ":" + encode(accountName);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer) + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

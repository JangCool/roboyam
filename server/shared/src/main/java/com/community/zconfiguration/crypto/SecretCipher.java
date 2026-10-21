package com.community.zconfiguration.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 설정값 암복호화 — AES-256-GCM.
 *
 * <p>암호문 형식: {@code v1:} + base64(IV 12바이트 ‖ 암호문 ‖ 태그 16바이트). 앞의 {@code v1} 은 키 버전이라
 * 나중에 키를 교체할 때 구분하는 데 쓴다. IV 는 암호화할 때마다 새로 뽑는다(GCM 은 같은 키로 IV 를 재사용하면 안 된다).
 * GCM 은 인증 암호화라서 암호문이 변조되면 복호화가 실패한다.
 *
 * <p>키는 base64 로 인코딩한 32바이트이고, 암호문과 같은 곳에 두지 않는다 — 환경 변수 {@code ROBOYAM_CRYPTO_KEY}
 * ({@code roboyam.crypto.key}) 로 받는다. 새 키는 {@link SecretCipherTool} 로 만든다.
 */
public final class SecretCipher {

    static final String VERSION_PREFIX = "v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    /** @param base64Key base64 로 인코딩한 32바이트 키 */
    public SecretCipher(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("암호화 키가 base64 형식이 아닙니다.");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException("암호화 키는 32바이트(base64)여야 합니다. 현재 " + raw.length + "바이트");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** 새 키를 만든다 (base64 32바이트). */
    public static String generateKey() {
        byte[] raw = new byte[KEY_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String cipherText) {
        if (!cipherText.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("지원하지 않는 암호문 형식입니다.");
        }
        try {
            byte[] in = Base64.getDecoder().decode(cipherText.substring(VERSION_PREFIX.length()));
            if (in.length <= IV_BYTES) {
                throw new IllegalArgumentException("암호문이 너무 짧습니다.");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 키가 다르거나 암호문이 변조된 경우. 원인 상세는 담지 않는다.
            throw new IllegalArgumentException("복호화에 실패했습니다. 키 또는 암호문을 확인하세요.");
        }
    }
}

package com.community.zconfiguration.crypto;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DB 에 저장하는 비밀 값(메일 비밀번호, 소셜 로그인 시크릿)을 암호화하는 빈. 암호화는 {@link SecretCipher}(AES-256-GCM, {@code v1:} 형식)가 한다.
 *
 * <p>키는 {@code roboyam.crypto.key}(환경 변수 {@code ROBOYAM_CRYPTO_KEY}, base64 32바이트)다. <b>키가 없어도 앱은 정상 기동</b>하고
 * 암호화가 필요한 쓰기만 {@code 503 CRYPTO_KEY_MISSING} 이다. 값이 있는데 형식이 틀리면 조용히 잘못된 키로 저장하지 않도록 기동을 멈춘다.
 * 키 값은 로그·오류 어디에도 싣지 않는다.
 */
@Component
public class SecretEncryptor {

    private final SecretCipher cipher;

    public SecretEncryptor(@Value("${roboyam.crypto.key:}") String key) {
        this.cipher = key == null || key.isBlank() ? null : new SecretCipher(key);
    }

    /** 암호화 키가 설정돼 있는가 */
    public boolean isConfigured() {
        return cipher != null;
    }

    /**
     * 암호화한다.
     *
     * @return {@code v1:} 로 시작하는 암호문
     * @throws CommonException 키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     */
    public String encrypt(String plain) {
        requireConfigured();
        return cipher.encrypt(plain);
    }

    /**
     * 복호화한다. 호출하는 쪽이 {@link #isConfigured()} 를 먼저 본다.
     *
     * @throws CommonException          키가 없을 때({@code 503 CRYPTO_KEY_MISSING})
     * @throws IllegalArgumentException 키가 다르거나 암호문이 깨졌을 때(원인 상세는 담지 않는다)
     */
    public String decrypt(String cipherText) {
        requireConfigured();
        return cipher.decrypt(cipherText);
    }

    /** 키가 없으면 {@code 503 CRYPTO_KEY_MISSING} */
    public void requireConfigured() {
        if (cipher == null) {
            throw new CommonException(ErrorCode.CRYPTO_KEY_MISSING);
        }
    }
}

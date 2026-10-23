package com.community.mfa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.mfa.totp.Base32;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.CommonException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 비밀키 20바이트 무작위(base32) · ROBOYAM_CRYPTO_KEY 로 암호화(키 없으면 503 CRYPTO_KEY_MISSING) · otpauth 주소(issuer = 사이트 이름, 계정 = 이메일, URL 인코딩) */
class MfaSecretsTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void 비밀키는_20바이트_무작위이고_base32_32자다() {
        String a = MfaSecrets.newSecret();
        String b = MfaSecrets.newSecret();

        assertThat(a).hasSize(32).matches("[A-Z2-7]{32}").isNotEqualTo(b);
        assertThat(Base32.decode(a)).hasSize(MfaSecrets.SECRET_BYTES).hasSize(20);
    }

    @Test
    void 암호화한_값에는_평문이_없고_풀면_같은_비밀키다() {
        MfaSecrets secrets = new MfaSecrets(new SecretEncryptor(KEY));
        String secret = MfaSecrets.newSecret();

        String encrypted = secrets.encrypt(secret);

        assertThat(encrypted).startsWith("v1:").doesNotContain(secret);
        assertThat(secrets.decrypt(encrypted)).isEqualTo(Base32.decode(secret));
    }

    @Test
    void 키가_없으면_503_CRYPTO_KEY_MISSING() {
        MfaSecrets secrets = new MfaSecrets(new SecretEncryptor(""));

        assertThatThrownBy(secrets::requireKey).isInstanceOfSatisfying(CommonException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo("CRYPTO_KEY_MISSING");
            assertThat(e.getStatus().value()).isEqualTo(503);
        });
        assertThatThrownBy(() -> secrets.encrypt("ABC")).isInstanceOf(CommonException.class);
    }

    @Test
    void otpauth_주소는_issuer_와_이메일을_URL_인코딩한다() {
        String uri = MfaSecrets.otpauthUri("My Site & Co", "ops+1@example.com", "JBSWY3DPEHPK3PXP");

        assertThat(uri).isEqualTo("otpauth://totp/My%20Site%20%26%20Co:ops%2B1%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=My%20Site%20%26%20Co"
                + "&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void 한글_사이트_이름도_인코딩한다() {
        assertThat(MfaSecrets.otpauthUri("로보얌", "a@b.c", "AAAA")).startsWith("otpauth://totp/%EB%A1%9C%EB%B3%B4%EC%96%8C:a%40b.c?").contains("issuer=%EB%A1%9C");
    }
}

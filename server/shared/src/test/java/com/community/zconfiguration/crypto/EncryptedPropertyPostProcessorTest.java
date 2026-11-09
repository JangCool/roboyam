package com.community.zconfiguration.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class EncryptedPropertyPostProcessorTest {

    private final String key = SecretCipher.generateKey();
    private final SecretCipher cipher = new SecretCipher(key);

    /** 운영 설치처럼 환경 변수(ROBOYAM_STORAGE_S3_SECRET_KEY=ENC(...))로 받은 값이 roboyam.storage.s3.secret-key 에 평문으로 묶인다 */
    @Test
    void 환경_변수의_암호문은_환경_변수식_이름으로_묶여도_평문이다() {
        StandardEnvironment environment = environment(
                Map.of("ROBOYAM_CRYPTO_KEY", key, "ROBOYAM_STORAGE_S3_SECRET_KEY", "ENC(" + cipher.encrypt("plain-secret") + ")"),
                Map.of("roboyam.crypto.key", "${ROBOYAM_CRYPTO_KEY:}", "roboyam.storage.s3.secret-key", "${ROBOYAM_STORAGE_S3_SECRET_KEY:}"));

        new EncryptedPropertyPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(bind(environment, "roboyam.storage.s3.secret-key")).isEqualTo("plain-secret");
        assertThat(environment.getProperty("roboyam.storage.s3.secret-key")).isEqualTo("plain-secret");
        assertThat(environment.getProperty("ROBOYAM_STORAGE_S3_SECRET_KEY")).isEqualTo("plain-secret");
    }

    /** yml 이 다른 이름의 환경 변수를 가리키는 경우(spring.datasource.password: ${DB_PASSWORD}) — 예전부터 되던 길 */
    @Test
    void 다른_이름의_환경_변수를_가리키는_설정도_평문이다() {
        StandardEnvironment environment = environment(
                Map.of("ROBOYAM_CRYPTO_KEY", key, "DB_PASSWORD", "ENC(" + cipher.encrypt("db-pass") + ")"),
                Map.of("roboyam.crypto.key", "${ROBOYAM_CRYPTO_KEY:}", "spring.datasource.password", "${DB_PASSWORD}"));

        new EncryptedPropertyPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(bind(environment, "spring.datasource.password")).isEqualTo("db-pass");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("db-pass");
    }

    /** 설정 파일에 바로 적은 암호문 */
    @Test
    void 설정_파일의_암호문도_평문이다() {
        StandardEnvironment environment = environment(Map.of(),
                Map.of("roboyam.crypto.key", key, "roboyam.storage.s3.secret-key", "ENC(" + cipher.encrypt("yml-secret") + ")"));

        new EncryptedPropertyPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(bind(environment, "roboyam.storage.s3.secret-key")).isEqualTo("yml-secret");
    }

    private static StandardEnvironment environment(Map<String, Object> env, Map<String, Object> yml) {
        StandardEnvironment environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new HashMap<>(env)));
        sources.addLast(new MapPropertySource("application.yml", new HashMap<>(yml)));
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    private static String bind(StandardEnvironment environment, String name) {
        return Binder.get(environment).bind(name, String.class).orElse(null);
    }
}

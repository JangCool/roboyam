package com.community.zconfiguration.crypto;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 프로퍼티의 {@code ENC(...)} 값을 기동 초기에 복호화해 같은 이름으로 덮어쓴다.
 *
 * <p>DB 비밀번호가 대표적인 대상이다. 데이터소스는 Spring Boot 기본 구성(Hikari)을 그대로 쓰고,
 * 설정 파일에는 암호문만 둔다.
 *
 * <pre>{@code
 * spring:
 *   datasource:
 *     url: jdbc:postgresql://db:5432/roboyam
 *     username: roboyam
 *     password: ENC(v1:q8F...)        # 평문 대신 암호문. 기동하면 복호화되어 Hikari 에 전달된다
 * }</pre>
 *
 * <p>{@code password: ${DB_PASSWORD}} 처럼 환경 변수를 거쳐도 된다 — 값을 풀어 본 결과가 {@code ENC(...)} 이면 복호화한다.
 *
 * <p>복호화 키는 {@code roboyam.crypto.key}(환경 변수 {@code ROBOYAM_CRYPTO_KEY}) 로 받는다. {@code ENC(...)} 값이
 * 하나도 없으면 키를 요구하지 않는다(키 없이 평문으로 뜨는 local 개발을 막지 않는다). 값이 있는데 키가 없거나
 * 복호화에 실패하면 기동을 멈춘다. 오류 메시지에는 프로퍼티 이름만 담고 값은 담지 않는다.
 *
 * <p>설정 파일(config data)이 다 읽힌 뒤에 돌도록 가장 늦은 순서로 둔다.
 */
public class EncryptedPropertyPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String KEY_PROPERTY = "roboyam.crypto.key";
    static final String SOURCE_NAME = "decryptedProperties";

    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> decrypted = new LinkedHashMap<>();
        SecretCipher cipher = null;

        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable) || SOURCE_NAME.equals(source.getName())) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                Object raw = enumerable.getProperty(name);
                if (!(raw instanceof String text)) {
                    continue;
                }
                // 우선순위가 높은 소스가 이미 덮어쓴 이름이면 건너뛴다 (환경 변수가 yml 을 이기는 경우 등)
                String value = environment.resolvePlaceholders(text).trim();
                if (!isEncrypted(value) || decrypted.containsKey(name) || !isEffective(environment, enumerable, name)) {
                    continue;
                }
                if (cipher == null) {
                    cipher = cipherFrom(environment, name);
                }
                decrypted.put(name, decrypt(cipher, value, name));
            }
        }

        if (!decrypted.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, decrypted));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static boolean isEncrypted(String value) {
        return value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    /** 이 이름의 최종 값이 이 소스의 것일 때만 복호화한다 (더 높은 소스가 평문으로 덮었으면 그쪽을 따른다). */
    private static boolean isEffective(ConfigurableEnvironment environment, PropertySource<?> source, String name) {
        for (PropertySource<?> candidate : environment.getPropertySources()) {
            if (candidate.containsProperty(name)) {
                return candidate == source;
            }
        }
        return false;
    }

    private static SecretCipher cipherFrom(ConfigurableEnvironment environment, String firstProperty) {
        String key = environment.getProperty(KEY_PROPERTY);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "암호화된 설정값(" + firstProperty + ")이 있지만 복호화 키가 없습니다. 환경 변수 ROBOYAM_CRYPTO_KEY 를 설정하세요.");
        }
        try {
            return new SecretCipher(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ROBOYAM_CRYPTO_KEY 가 올바르지 않습니다: " + e.getMessage());
        }
    }

    private static String decrypt(SecretCipher cipher, String value, String name) {
        String inner = value.substring(PREFIX.length(), value.length() - SUFFIX.length());
        try {
            return cipher.decrypt(inner);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("설정값 복호화에 실패했습니다: " + name + " — " + e.getMessage());
        }
    }
}

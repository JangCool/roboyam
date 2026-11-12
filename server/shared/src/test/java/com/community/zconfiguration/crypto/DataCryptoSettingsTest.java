package com.community.zconfiguration.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 키 자리의 실제 설정 파일(docs/domain/commerce.md 10-9) — local 만 개발용 키가 있고, 공통 설정(dev·prod)은 기본값이 없어 키가 빠지면 「키 없음」(503)으로 뜬다.
 * 환경 변수 {@code IROBIT_DATA_KEY}·{@code IROBIT_INDEX_KEY} 가 시험 PC 에 있으면 그 값이 먼저라 이 시험의 판정이 달라지므로, 파일만 읽어 환경 변수 없는 환경으로 본다.
 */
class DataCryptoSettingsTest {

    private static StandardEnvironment load(String... files) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : files) { // 뒤의 파일이 앞의 것을 덮는다(프로파일 파일이 공통 파일을 덮는 것과 같다)
            loader.load(file, new ClassPathResource(file)).forEach(source -> environment.getPropertySources().addFirst(source));
        }
        return environment;
    }

    @Test
    void local_은_개발용_키로_암복호화와_해시가_된다() throws IOException {
        StandardEnvironment local = load("irobit-shared.yml", "irobit-shared-local.yml");

        DataCipher cipher = new DataCipher(local.resolvePlaceholders("${irobit.data-crypto.key:}"), local.resolvePlaceholders("${irobit.data-crypto.previous-keys:}"));
        BlindIndex index = new BlindIndex(local.resolvePlaceholders("${irobit.data-crypto.index-key:}"));

        assertThat(cipher.activeVersion()).isEqualTo(1);
        DataPurpose purpose = DataPurpose.of("withdrawn.email");
        assertThat(cipher.decrypt(purpose, cipher.encrypt(purpose, "member@example.com"))).isEqualTo("member@example.com");
        assertThat(index.hash(DataPurpose.of("identity.di"), "value")).hasSize(64);
    }

    @Test
    void 공통_설정에는_키_기본값이_없어_키_없음으로_뜬다() throws IOException {
        StandardEnvironment shared = load("irobit-shared.yml");

        assertThat(new DataCipher(shared.resolvePlaceholders("${irobit.data-crypto.key:}"), shared.resolvePlaceholders("${irobit.data-crypto.previous-keys:}")).isConfigured()).isFalse();
        assertThat(new BlindIndex(shared.resolvePlaceholders("${irobit.data-crypto.index-key:}")).isConfigured()).isFalse();
        assertThat(shared.resolvePlaceholders("${irobit.crypto.key:}")).isEmpty(); // 설정 비밀값 키도 dev·prod 는 환경 변수로만 받는다
    }

    @Test
    void 데이터_키와_해시_키와_설정_비밀값_키는_서로_다른_값이다() throws IOException {
        StandardEnvironment local = load("irobit-shared.yml", "irobit-shared-local.yml");
        String dataKey = local.resolvePlaceholders("${irobit.data-crypto.key:}");
        String indexKey = local.resolvePlaceholders("${irobit.data-crypto.index-key:}");
        String secretKey = local.resolvePlaceholders("${irobit.crypto.key:}"); // 설정 비밀값 키(IROBIT_CRYPTO_KEY) — local 은 개발용 값이 따로 있다

        assertThat(dataKey.substring(dataKey.indexOf(':') + 1)).isNotEqualTo(indexKey);
        assertThat(secretKey).isNotBlank().isNotEqualTo(dataKey.substring(dataKey.indexOf(':') + 1)).isNotEqualTo(indexKey);
    }
}

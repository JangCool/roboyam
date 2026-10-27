package com.community.portone.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.portone.dto.PortOneSettingKey;
import com.community.portone.repository.master.PortOneSettingStore;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * local 포트원 시드 — 표가 비어 있을 때만 다섯 칸을 암호화해 넣고(바꾼 사람 없음), 화면에서 저장·지운 칸이 있거나 dev·prod 이거나 설정 비밀값 키가 없으면 넣지 않는다.
 * 시드 값은 local 설정 파일에만 있다. 저장소의 「줄이 없을 때만 넣기」는 실제 DB 로 본다.
 */
class PortOneLocalSeederTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final PortOneSeedProperties SEED = new PortOneSeedProperties(true, "store-seed", "secret-seed", "channel-identity", "channel-payment", "whsec_c2VlZA==");

    /** 메모리 저장소 */
    static class MemoryStore extends PortOneSettingStore {

        final List<Row> rows = new ArrayList<>();

        MemoryStore() {
            super(new DriverManagerDataSource());
        }

        @Override
        public List<Row> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public boolean seedIfAbsent(String key, String valueCipher, Instant now) {
            if (rows.stream().anyMatch(row -> row.key().equals(key))) {
                return false;
            }
            return rows.add(new Row(key, valueCipher, now, null, null));
        }
    }

    private final MemoryStore store = new MemoryStore();

    private PortOneLocalSeeder seeder(PortOneSeedProperties seed, String cryptoKey, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new PortOneLocalSeeder(seed, store, new SecretEncryptor(cryptoKey), environment, new StaticListableBeanFactory().getBeanProvider(Clock.class));
    }

    @Test
    void 표가_비어_있으면_다섯_칸을_암호화해_넣고_바꾼_사람은_없다() {
        assertThat(seeder(SEED, KEY, "local").seed()).isEqualTo(5);

        SecretEncryptor encryptor = new SecretEncryptor(KEY);
        Map<String, String> plain = store.rows.stream().collect(Collectors.toMap(PortOneSettingStore.Row::key, row -> encryptor.decrypt(row.valueCipher())));
        assertThat(plain).containsEntry("storeId", "store-seed").containsEntry("apiSecret", "secret-seed").containsEntry("identityChannelKey", "channel-identity")
                .containsEntry("paymentChannelKey", "channel-payment").containsEntry("webhookSecret", "whsec_c2VlZA==");
        assertThat(store.rows).allSatisfy(row -> {
            assertThat(row.valueCipher()).startsWith("v1:");
            assertThat(row.updatedBy()).isNull();
        });
    }

    @Test
    void 화면에서_저장하거나_지운_칸이_하나라도_있으면_손대지_않는다() {
        store.rows.add(new PortOneSettingStore.Row("storeId", null, Instant.now(), 1L, "op")); // 지운 칸

        assertThat(seeder(SEED, KEY, "local").seed()).isZero();
        assertThat(store.rows).hasSize(1);
    }

    @Test
    void dev_prod_이거나_설정_비밀값_키가_없거나_시드가_비거나_꺼져_있으면_넣지_않는다() {
        assertThat(seeder(SEED, KEY, "dev").seed()).isZero();
        assertThat(seeder(SEED, KEY, "prod").seed()).isZero();
        assertThat(seeder(SEED, "", "local").seed()).isZero();
        assertThat(seeder(new PortOneSeedProperties(true, null, " ", null, null, null), KEY, "local").seed()).isZero();
        assertThat(seeder(new PortOneSeedProperties(false, "s", "a", "i", "p", "whsec_c2VlZA=="), KEY, "local").seed()).isZero();
        assertThat(store.rows).isEmpty();
    }

    @Test
    void Gradle_시험_작업은_시드를_끈다() {
        assertThat(System.getProperty("roboyam.portone.seed.enabled")).isEqualTo("false"); // 앱 시험이 local 프로필로 떠 시험 DB 를 채우지 않게
    }

    @Test
    void 시드는_local_설정_파일에만_다섯_칸이_있고_공통_설정에는_없다() throws IOException {
        StandardEnvironment local = load("roboyam-shared.yml", "roboyam-shared-local.yml");
        StandardEnvironment shared = load("roboyam-shared.yml");

        for (String field : List.of("store-id", "api-secret", "identity-channel-key", "payment-channel-key", "webhook-secret")) {
            assertThat(local.resolvePlaceholders("${roboyam.portone.seed." + field + ":}")).as(field).isNotBlank();
            assertThat(shared.resolvePlaceholders("${roboyam.portone.seed." + field + ":}")).as(field).isEmpty();
        }
        assertThat(local.resolvePlaceholders("${roboyam.portone.seed.webhook-secret:}")).startsWith("whsec_");
    }

    @Test
    void 저장소는_줄이_없을_때만_넣는다() {
        JdbcTemplate jdbc = new JdbcTemplate(CommerceTestDatabase.migrated());
        PortOneSettingStore real = new PortOneSettingStore(CommerceTestDatabase.migrated());
        String key = "zzSeed" + System.nanoTime();
        try {
            assertThat(real.seedIfAbsent(key, "v1:AAAA", Instant.now())).isTrue();
            assertThat(real.seedIfAbsent(key, "v1:BBBB", Instant.now())).isFalse();
            assertThat(jdbc.queryForMap("SELECT value_cipher, updated_by FROM portone_settings WHERE key = ?", key)).containsEntry("value_cipher", "v1:AAAA")
                    .containsEntry("updated_by", null);
        } finally {
            jdbc.update("DELETE FROM portone_settings WHERE key = ?", key);
        }
    }

    private static StandardEnvironment load(String... files) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME); // 시험 PC 의 환경 변수와 상관없이 파일만 본다
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : files) {
            loader.load(file, new ClassPathResource(file)).forEach(source -> environment.getPropertySources().addFirst(source));
        }
        return environment;
    }
}

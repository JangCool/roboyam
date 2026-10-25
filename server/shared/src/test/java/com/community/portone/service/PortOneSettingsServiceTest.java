package com.community.portone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.identity.config.IdentityProperties;
import com.community.identity.dto.IdentityProviderKind;
import com.community.portone.repository.master.PortOneSettingStore;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.CommonException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 포트원 설정(세션 정리 결정 9) — 암호화 키가 없으면 저장은 503, 풀 수 없는 값은 「설정 안 됨」으로 본다(값·암호문은 응답에 없다). DB 는 흉내 낸 저장소로 대신한다.
 * (실제 DB·감사·화면 모양은 api 의 {@code PortOneIdentitySettingsTest})
 */
class PortOneSettingsServiceTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    /** 메모리 저장소 — 쓰기 횟수를 센다 */
    static class MemoryStore extends PortOneSettingStore {

        final List<Row> rows = new ArrayList<>();
        int writes;

        MemoryStore() {
            super(new DriverManagerDataSource());
        }

        @Override
        public List<Row> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public void upsert(String key, String valueCipher, long updatedBy, Instant now) {
            writes++;
            rows.removeIf(row -> row.key().equals(key));
            rows.add(new Row(key, valueCipher, now, updatedBy, "op"));
        }
    }

    private final MemoryStore store = new MemoryStore();

    private PortOneSettingsService service(SecretEncryptor encryptor) {
        return new PortOneSettingsService(store, encryptor, new IdentityProperties(IdentityProviderKind.PORTONE, Duration.ofMinutes(30)), null,
                new StaticListableBeanFactory().getBeanProvider(Clock.class));
    }

    @Test
    void 암호화_키가_없으면_저장은_503_CRYPTO_KEY_MISSING_이고_화면은_그것을_알린다() {
        PortOneSettingsService service = service(new SecretEncryptor(""));
        assertThatThrownBy(() -> service.saveIdentity("store", "secret", "channel", 1L, "127.0.0.1")).isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("CRYPTO_KEY_MISSING");
        assertThat(store.writes).isZero();
        assertThat(service.view().cryptoKeyConfigured()).isFalse();
        assertThat(service.view().identityReady()).isFalse();
        assertThat(service.view().identityProvider()).isEqualTo(IdentityProviderKind.PORTONE);
    }

    @Test
    void 다른_키로_잠긴_값은_풀지_못해_설정_안_됨으로_본다() {
        String foreign = new SecretEncryptor("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=").encrypt("value");
        Instant at = Instant.now();
        for (String key : List.of("storeId", "apiSecret", "identityChannelKey")) {
            store.rows.add(new PortOneSettingStore.Row(key, foreign, at, 1L, "op"));
        }

        PortOneSettingsService service = service(new SecretEncryptor(KEY));
        assertThat(service.identityReady()).isTrue(); // 칸은 다 있다
        assertThat(service.identityCredentials()).isEmpty(); // 그러나 풀 수 없다 — 본인인증은 503 IDENTITY_NOT_CONFIGURED 가 된다
        assertThat(store.rows.get(0).toString()).doesNotContain(foreign);
    }
}

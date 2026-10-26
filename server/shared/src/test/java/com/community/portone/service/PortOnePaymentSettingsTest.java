package com.community.portone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.identity.config.IdentityProperties;
import com.community.identity.dto.IdentityProviderKind;
import com.community.portone.dto.PortOnePaymentCredentials;
import com.community.portone.dto.PortOneSettingKey;
import com.community.portone.dto.response.PortOneFieldStateView;
import com.community.portone.repository.master.PortOneSettingStore;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * 포트원 결제 칸(계약 결정 14·15 — 2a-2 는 코드만, 화면·관리자 API 는 2a-3): 묶음을 공통·본인인증·결제로 나눴고 키 문자열은 그대로, {@code paymentReady} = 결제 4칸 + 암호화 키,
 * 결제 값은 결제 게이트웨이만 푼다(감춘 {@code toString}). 기존 본인인증 화면·준비 판정은 그대로다.
 */
class PortOnePaymentSettingsTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final PortOneSettingsServiceTest.MemoryStore store = new PortOneSettingsServiceTest.MemoryStore();

    private PortOneSettingsService service(SecretEncryptor encryptor) {
        return new PortOneSettingsService(store, encryptor, new IdentityProperties(IdentityProviderKind.PORTONE, Duration.ofMinutes(30)), null,
                new StaticListableBeanFactory().getBeanProvider(Clock.class));
    }

    private void put(SecretEncryptor encryptor, Map<String, String> values) {
        values.forEach((key, value) -> store.rows.add(new PortOneSettingStore.Row(key, encryptor.encrypt(value), Instant.now(), 1L, "op")));
    }

    @Test
    void 키_문자열은_그대로이고_묶음은_공통_본인인증_결제다() {
        assertThat(PortOneSettingKey.STORE_ID.key()).isEqualTo("storeId");
        assertThat(PortOneSettingKey.API_SECRET.key()).isEqualTo("apiSecret");
        assertThat(PortOneSettingKey.IDENTITY_CHANNEL_KEY.key()).isEqualTo("identityChannelKey");
        assertThat(PortOneSettingKey.PAYMENT_CHANNEL_KEY.key()).isEqualTo("paymentChannelKey");
        assertThat(PortOneSettingKey.WEBHOOK_SECRET.key()).isEqualTo("webhookSecret");
        assertThat(PortOneSettingKey.of(PortOneSettingKey.Group.COMMON)).containsExactly(PortOneSettingKey.STORE_ID, PortOneSettingKey.API_SECRET);
        assertThat(PortOneSettingKey.of(PortOneSettingKey.Group.IDENTITY)).containsExactly(PortOneSettingKey.IDENTITY_CHANNEL_KEY);
        assertThat(PortOneSettingKey.of(PortOneSettingKey.Group.PAYMENT)).containsExactly(PortOneSettingKey.PAYMENT_CHANNEL_KEY, PortOneSettingKey.WEBHOOK_SECRET);
        assertThat(PortOneSettingKey.paymentKeys()).containsExactly(PortOneSettingKey.STORE_ID, PortOneSettingKey.API_SECRET, PortOneSettingKey.PAYMENT_CHANNEL_KEY,
                PortOneSettingKey.WEBHOOK_SECRET);
    }

    @Test
    void paymentReady_는_결제_4칸과_암호화_키가_모두_있어야_참이다() {
        SecretEncryptor encryptor = new SecretEncryptor(KEY);
        PortOneSettingsService service = service(encryptor);
        put(encryptor, Map.of("storeId", "store-1", "apiSecret", "secret-1", "paymentChannelKey", "channel-pay"));
        assertThat(service.paymentReady()).as("웹훅 비밀키 없음").isFalse();
        assertThat(service.paymentCredentials()).isEmpty();
        put(encryptor, Map.of("webhookSecret", "whsec_abc"));
        assertThat(service.paymentReady()).isTrue();
        PortOnePaymentCredentials credentials = service.paymentCredentials().orElseThrow();
        assertThat(credentials.storeId()).isEqualTo("store-1");
        assertThat(credentials.channelKey()).isEqualTo("channel-pay");
        assertThat(credentials.apiSecret()).isEqualTo("secret-1");
        assertThat(credentials.webhookSecret()).isEqualTo("whsec_abc");
        assertThat(credentials.toString()).doesNotContain("secret-1").doesNotContain("whsec_abc");

        assertThat(service(new SecretEncryptor("")).paymentReady()).as("암호화 키 없음").isFalse();
        assertThat(service(new SecretEncryptor("")).paymentCredentials()).isEmpty();
    }

    @Test
    void 본인인증_화면과_준비_판정은_예전_세_칸_그대로다() {
        SecretEncryptor encryptor = new SecretEncryptor(KEY);
        PortOneSettingsService service = service(encryptor);
        put(encryptor, Map.of("storeId", "s", "apiSecret", "a", "paymentChannelKey", "p", "webhookSecret", "w"));
        assertThat(service.identityReady()).as("본인인증 채널 키 없음").isFalse();
        assertThat(service.view().fields()).extracting(PortOneFieldStateView::key).containsExactly("storeId", "apiSecret", "identityChannelKey");
        put(encryptor, Map.of("identityChannelKey", "i"));
        assertThat(service.identityReady()).isTrue();
        assertThat(service.identityCredentials()).isPresent();
        assertThat(List.of(service.view().toString())).allSatisfy(text -> assertThat(text).doesNotContain("whsec"));
    }
}

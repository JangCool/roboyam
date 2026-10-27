package com.community.portone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.identity.config.IdentityProperties;
import com.community.identity.dto.IdentityProviderKind;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.portone.dto.PortOnePaymentCredentials;
import com.community.portone.dto.PortOneSettingKey;
import com.community.portone.dto.response.PortOneFieldStateView;
import com.community.portone.dto.response.PortOneSettingsView;
import com.community.portone.repository.master.PortOneSettingStore;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * 포트원 결제 칸(계약 결정 14·15): 묶음을 공통·본인인증·결제로 나눴고 키 문자열은 그대로, {@code paymentReady} = 결제 4칸 + 암호화 키, 결제 값은 결제 게이트웨이만 푼다(감춘 {@code toString}).
 * 2a-3: 화면은 다섯 칸·결제 구현·결제 준비를 싣고, 저장은 카드 단위(다른 카드의 칸은 받지 않는다), 웹훅 비밀키는 {@code whsec_} + base64 만. 본인인증 준비 판정은 세 칸 그대로.
 */
class PortOnePaymentSettingsTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final PortOneSettingsServiceTest.MemoryStore store = new PortOneSettingsServiceTest.MemoryStore();

    private PortOneSettingsService service(SecretEncryptor encryptor) {
        return new PortOneSettingsService(store, encryptor, new IdentityProperties(IdentityProviderKind.PORTONE, Duration.ofMinutes(30)), com.community.payment.config.PaymentProperties.DEFAULTS, null,
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
    void 본인인증_준비_판정은_세_칸이고_화면은_다섯_칸과_결제_구현_결제_준비를_싣는다() {
        SecretEncryptor encryptor = new SecretEncryptor(KEY);
        PortOneSettingsService service = service(encryptor);
        put(encryptor, Map.of("storeId", "s", "apiSecret", "a", "paymentChannelKey", "p", "webhookSecret", "whsec_d2hzZWM="));
        assertThat(service.identityReady()).as("본인인증 채널 키 없음").isFalse();
        PortOneSettingsView view = service.view();
        assertThat(view.fields()).extracting(PortOneFieldStateView::key).containsExactly("storeId", "apiSecret", "identityChannelKey", "paymentChannelKey", "webhookSecret");
        assertThat(view.fields()).extracting(PortOneFieldStateView::configured).containsExactly(true, true, false, true, true);
        assertThat(view.paymentGateway()).as("배포 설정 roboyam.payment.gateway").isEqualTo(PaymentGatewayKind.PORTONE);
        assertThat(view.paymentReady()).isTrue();
        assertThat(view.identityReady()).isFalse();
        put(encryptor, Map.of("identityChannelKey", "i"));
        assertThat(service.identityReady()).isTrue();
        assertThat(service.identityCredentials()).isPresent();
        assertThat(List.of(service.view().toString())).allSatisfy(text -> assertThat(text).doesNotContain("whsec").doesNotContain("d2hzZWM="));
        assertThat(service(new SecretEncryptor("")).view().paymentReady()).as("암호화 키가 없으면 결제 준비 안 됨").isFalse();
    }

    @Test
    void 웹훅_비밀키는_whsec_뒤에_base64_가_있어야_하고_다른_칸은_형식을_보지_않는다() {
        assertThat(PortOneSettingsService.webhookSecretShape("whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw")).isTrue();
        assertThat(PortOneSettingsService.webhookSecretShape("whsec_")).as("뒤가 비었다").isFalse();
        assertThat(PortOneSettingsService.webhookSecretShape("MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw")).as("whsec_ 없음").isFalse();
        assertThat(PortOneSettingsService.webhookSecretShape("whsec_not base64!")).isFalse();
        assertThat(PortOneSettingsService.violation(PortOneSettingKey.WEBHOOK_SECRET, "secret").key()).isEqualTo("portone.field.webhookSecret");
        assertThat(PortOneSettingsService.violation(PortOneSettingKey.PAYMENT_CHANNEL_KEY, "channel-key-plain")).isNull();
        assertThat(PortOneSettingsService.violation(PortOneSettingKey.PAYMENT_CHANNEL_KEY, "").key()).isEqualTo("portone.field.required");
        assertThat(PortOneSettingsService.violation(PortOneSettingKey.PAYMENT_CHANNEL_KEY, "x".repeat(501)).key()).isEqualTo("portone.field.max");

        PortOneSettingsService service = service(new SecretEncryptor(KEY));
        ValidationException bad = catchThrowableOfType(ValidationException.class, () -> service.save(PortOneSettingKey.Group.PAYMENT,
                Map.of(PortOneSettingKey.WEBHOOK_SECRET, "plain-secret", PortOneSettingKey.PAYMENT_CHANNEL_KEY, "  "), 1L, "127.0.0.1"));
        assertThat(bad.getFields()).containsOnlyKeys("webhookSecret", "paymentChannelKey");
        assertThat(store.writes).as("하나라도 틀리면 아무 칸도 저장하지 않는다").isZero();
    }

    @Test
    void 카드는_자기_칸만_받는다_다른_카드의_칸이면_거절() {
        PortOneSettingsService service = service(new SecretEncryptor(KEY));
        assertThatThrownBy(() -> service.save(PortOneSettingKey.Group.IDENTITY, Map.of(PortOneSettingKey.STORE_ID, "store"), 1L, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(PortOneSettingKey.Group.COMMON, Map.of(), 1L, "127.0.0.1")).isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("VALIDATION");
        assertThat(PortOneSettingsService.groupName(PortOneSettingKey.Group.COMMON)).isEqualTo("common");
        assertThat(PortOneSettingsService.groupName(PortOneSettingKey.Group.IDENTITY)).isEqualTo("identity");
        assertThat(PortOneSettingsService.groupName(PortOneSettingKey.Group.PAYMENT)).isEqualTo("payment");
        assertThat(store.writes).isZero();
    }
}

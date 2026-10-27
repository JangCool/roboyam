package com.community.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.audit.dto.AuditActions;
import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.response.IdentityStartView;
import com.community.identity.exception.IdentityNotConfiguredException;
import com.community.identity.service.IdentityVerificationService;
import com.community.portone.dto.PortOneSettingKey;
import com.community.portone.dto.response.PortOneFieldStateView;
import com.community.portone.dto.response.PortOneSettingsView;
import com.community.portone.exception.PortOneFieldNotFoundException;
import com.community.portone.service.PortOneSettingsService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * 포트원 구현(roboyam.identity.provider=portone)의 설정 — 상점 ID·API 비밀키·본인인증 채널 키는 DB 에 {@code SecretEncryptor} 암호문으로, 화면·감사에는 값이 없다.
 * 칸이 하나라도 비면 인증 시작이 503 {@code IDENTITY_NOT_CONFIGURED}(계약 「0-4」 결정 9). 저장·지우기는 카드 단위(공통 = 상점 ID·API 비밀키, 본인인증 = 채널 키 — 2a 결정 14). 포트원 실제 호출은 하지 않는다(조회 응답 해석은 shared 의 {@code PortOneIdentityVerifierTest}).
 */
@TestPropertySource(properties = "roboyam.identity.provider=portone")
class PortOneIdentitySettingsTest extends CommerceApiTestBase {

    private static final String STORE = "store-11111111-2222-3333-4444-555555555555";
    private static final String SECRET = "portone-api-secret-value-9d8c7b";
    private static final String CHANNEL = "channel-key-aaaabbbb-cccc";
    private static final String IP = "127.0.0.1";

    @Autowired
    private PortOneSettingsService settings;
    @Autowired
    private IdentityVerificationService identities;

    /** 공통 카드(보낸 칸만) → 본인인증 카드 차례로 저장한다 — null 은 안 보낸 칸 */
    private PortOneSettingsView saveAll(String store, String secret, String channel, long operator) {
        Map<PortOneSettingKey, String> common = new EnumMap<>(PortOneSettingKey.class);
        if (store != null) {
            common.put(PortOneSettingKey.STORE_ID, store);
        }
        if (secret != null) {
            common.put(PortOneSettingKey.API_SECRET, secret);
        }
        PortOneSettingsView view = common.isEmpty() ? settings.view() : settings.save(PortOneSettingKey.Group.COMMON, common, operator, IP);
        return channel == null ? view : settings.save(PortOneSettingKey.Group.IDENTITY, Map.of(PortOneSettingKey.IDENTITY_CHANNEL_KEY, channel), operator, IP);
    }

    @BeforeEach
    @AfterEach
    void clearSettings() {
        jdbc.update("DELETE FROM portone_settings WHERE key IN ('storeId', 'apiSecret', 'identityChannelKey')"); // 이 시험이 쓰는 세 칸만
    }

    @Test
    void 칸이_비어_있으면_인증_시작은_503_이고_번호도_발급하지_않는다() {
        long member = newMember();
        saveAll(STORE, null, CHANNEL, newOperator()); // API 비밀키가 없다

        CommonException error = catchThrowableOfType(IdentityNotConfiguredException.class, () -> identities.start(member));
        assertThat(error.getStatus().value()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_verification_attempts WHERE user_id = ?", Integer.class, member)).isZero();
    }

    @Test
    void 세_칸이_있으면_포트원_SDK_값을_주고_비밀키는_주지_않는다() {
        saveAll(STORE, SECRET, CHANNEL, newOperator());
        long member = newMember();

        IdentityStartView view = identities.start(member);
        assertThat(view.provider()).isEqualTo(IdentityProviderKind.PORTONE);
        assertThat(view.storeId()).isEqualTo(STORE);
        assertThat(view.channelKey()).isEqualTo(CHANNEL);
        assertThat(view.toString()).doesNotContain(SECRET);
    }

    @Test
    void 값은_암호문으로만_저장되고_화면과_감사에는_값이_없다() {
        long operator = newOperator();
        PortOneSettingsView view = saveAll(" " + STORE + " ", SECRET, CHANNEL, operator);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT key, value_cipher FROM portone_settings WHERE key IN ('storeId', 'apiSecret', 'identityChannelKey')");
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> assertThat((String) row.get("value_cipher")).startsWith("v1:").doesNotContain(STORE).doesNotContain(SECRET)
                .doesNotContain(CHANNEL));
        assertThat(view.identityProvider()).isEqualTo(IdentityProviderKind.PORTONE);
        assertThat(view.cryptoKeyConfigured()).isTrue();
        assertThat(view.identityReady()).isTrue();
        assertThat(view.fields()).extracting(PortOneFieldStateView::key).containsExactly("storeId", "apiSecret", "identityChannelKey", "paymentChannelKey", "webhookSecret");
        assertThat(view.fields().subList(0, 3)).allMatch(PortOneFieldStateView::configured).allMatch(field -> field.updatedBy() != null && field.updatedAt() != null);
        assertThat(view.toString()).doesNotContain(STORE).doesNotContain(SECRET).doesNotContain(CHANNEL);
        assertThat(settings.identityCredentials().orElseThrow().storeId()).isEqualTo(STORE); // 앞뒤 공백은 지웠다

        String common = (String) lastAudit("PORTONE_SETTINGS", "common", AuditActions.PORTONE_SETTINGS_UPDATE).get("detail");
        String identity = (String) lastAudit("PORTONE_SETTINGS", "identity", AuditActions.PORTONE_SETTINGS_UPDATE).get("detail");
        assertThat(common).contains("storeId", "apiSecret").doesNotContain("identityChannelKey");
        assertThat(identity).contains("identityChannelKey").doesNotContain("storeId");
        assertThat(common + identity).doesNotContain(STORE).doesNotContain(SECRET).doesNotContain(CHANNEL);
    }

    @Test
    void 보낸_칸만_바꾸고_빈_값_긴_값_아무것도_안_보냄은_400() {
        saveAll(STORE, SECRET, CHANNEL, newOperator());
        saveAll(null, "new-secret-value", null, newOperator());
        assertThat(settings.identityCredentials().orElseThrow()).satisfies(c -> {
            assertThat(c.storeId()).isEqualTo(STORE);
            assertThat(c.apiSecret()).isEqualTo("new-secret-value");
            assertThat(c.channelKey()).isEqualTo(CHANNEL);
        });

        ValidationException blank = catchThrowableOfType(ValidationException.class, () -> saveAll("  ", null, null, newOperator()));
        assertThat(blank.getFields()).containsOnlyKeys("storeId");
        ValidationException longKey = catchThrowableOfType(ValidationException.class, () -> saveAll(null, null, "x".repeat(501), newOperator()));
        assertThat(longKey.getFields()).containsOnlyKeys("identityChannelKey");
        assertThatThrownBy(() -> settings.save(PortOneSettingKey.Group.IDENTITY, Map.of(), newOperator(), IP)).isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("VALIDATION");
    }

    @Test
    void 칸을_지우면_값만_사라지고_마지막_변경은_남는다_없는_칸은_404() {
        long operator = newOperator();
        saveAll(STORE, SECRET, CHANNEL, operator);

        settings.deleteField(PortOneSettingKey.Group.COMMON, "apiSecret", operator, IP);

        PortOneSettingsView view = settings.view();
        PortOneFieldStateView secret = view.fields().stream().filter(f -> f.key().equals("apiSecret")).findFirst().orElseThrow();
        assertThat(secret.configured()).isFalse();
        assertThat(secret.updatedAt()).isNotNull();
        assertThat(view.identityReady()).isFalse();
        assertThat(settings.identityCredentials()).isEmpty();
        assertThatThrownBy(() -> identities.start(newMember())).isInstanceOf(IdentityNotConfiguredException.class);
        assertThat((String) lastAudit("PORTONE_SETTINGS", "common", AuditActions.PORTONE_SETTINGS_DELETE).get("detail")).contains("apiSecret");

        assertThatThrownBy(() -> settings.deleteField(PortOneSettingKey.Group.COMMON, "apiSecret", operator, IP)).isInstanceOf(PortOneFieldNotFoundException.class);
        assertThatThrownBy(() -> settings.deleteField(PortOneSettingKey.Group.IDENTITY, "storeId", operator, IP)).as("다른 카드의 칸")
                .isInstanceOf(PortOneFieldNotFoundException.class);
        assertThatThrownBy(() -> settings.deleteField(PortOneSettingKey.Group.PAYMENT, "webhookSecret", operator, IP)).isInstanceOf(PortOneFieldNotFoundException.class);
    }
}

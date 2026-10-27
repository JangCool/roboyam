package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.identity.config.IdentityProperties;
import com.community.payment.config.PaymentProperties;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 포트원 설정 API(계약 「0-4」, 세션 정리 결정 9 + 2a 결정 14) — 카드 셋(공통 = 상점 ID·API 비밀키 · 본인인증 = 채널 키 · 결제 = 결제 채널 키·웹훅 비밀키)마다 저장·지우기.
 * 값은 어떤 응답·감사에도 없고 설정 여부·시각·바꾼 사람만, 감사는 대상 ID = 카드 이름 + 칸 이름만. 조회 {@code site.read} · 저장·지우기 {@code site.secret}.
 * 화면은 {@code paymentGateway}(배포 설정)·{@code paymentReady}(결제 4칸 + 암호화 키)를 싣고, 웹훅 비밀키는 {@code whsec_} + base64 만 받는다.
 * 설정 표는 사이트에 하나라 시험 앞뒤로 원래 줄을 되돌린다.
 */
class PortOneSettingsHttpTest extends Commerce04AdminTestBase {

    private static final String STORE_ID = "store-c04-7f3a9e";
    private static final String API_SECRET = "secret-c04-QmFzZTY0U2VjcmV0VmFsdWU";
    private static final String CHANNEL_KEY = "channel-key-c04-b1d2e3";
    private static final String PAYMENT_CHANNEL_KEY = "channel-key-pay-2a3-c9d8e7";
    private static final String WEBHOOK_SECRET = "whsec_MmEzV2ViaG9va1NlY3JldFZhbHVlMTIzNDU2";

    @Autowired
    private IdentityProperties identity;
    @Autowired
    private PaymentProperties payment;

    private List<Map<String, Object>> saved;

    @BeforeEach
    void keepRows() {
        saved = jdbc.queryForList("SELECT key, value_cipher, updated_at, updated_by FROM portone_settings");
        jdbc.update("DELETE FROM portone_settings");
    }

    @AfterEach
    void restoreRows() {
        jdbc.update("DELETE FROM portone_settings");
        for (Map<String, Object> row : saved) {
            jdbc.update("INSERT INTO portone_settings (key, value_cipher, updated_at, updated_by) VALUES (?, ?, ?, ?)", row.get("key"), row.get("value_cipher"),
                    row.get("updated_at"), row.get("updated_by"));
        }
    }

    @Test
    void 조회는_site_read_저장과_지우기는_세_카드_모두_site_secret_이_없으면_403() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));

        mvc.perform(get("/api/admin/portone-settings").cookie(none)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/admin/portone-settings").cookie(reader)).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/common"), reader).content("{\"storeId\":\"" + STORE_ID + "\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), reader).content("{\"identityChannelKey\":\"" + CHANNEL_KEY + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/payment"), reader).content("{\"webhookSecret\":\"" + WEBHOOK_SECRET + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/common/storeId"), reader)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/payment/webhookSecret"), reader)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portone_settings", Integer.class)).isZero();
    }

    @Test
    void 처음에는_다섯_칸_모두_비었고_지금_구현_둘과_암호화_키_준비_여부를_알린다() throws Exception {
        mvc.perform(get("/api/admin/portone-settings").cookie(login(operatorWith(Permission.SITE_READ)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.identityProvider").value(identity.provider().name())) // 이 서버의 roboyam.identity.provider
                .andExpect(jsonPath("$.data.paymentGateway").value(payment.gateway().name())) // 이 서버의 roboyam.payment.gateway
                .andExpect(jsonPath("$.data.cryptoKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.identityReady").value(false))
                .andExpect(jsonPath("$.data.paymentReady").value(false))
                .andExpect(jsonPath("$.data.fields.length()").value(5))
                .andExpect(jsonPath("$.data.fields[0].key").value("storeId"))
                .andExpect(jsonPath("$.data.fields[1].key").value("apiSecret"))
                .andExpect(jsonPath("$.data.fields[2].key").value("identityChannelKey"))
                .andExpect(jsonPath("$.data.fields[3].key").value("paymentChannelKey"))
                .andExpect(jsonPath("$.data.fields[4].key").value("webhookSecret"))
                .andExpect(jsonPath("$.data.fields[0].configured").value(false))
                .andExpect(jsonPath("$.data.fields[4].updatedAt").isEmpty());
    }

    @Test
    void 카드마다_보낸_칸만_암호화해_저장하고_값은_응답과_감사_어디에도_없다_paymentReady_는_네_칸이_다_있어야() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);

        MvcResult common = mvc.perform(withCsrf(put("/api/admin/portone-settings/common"), session)
                        .content("{\"storeId\":\"" + STORE_ID + "\",\"apiSecret\":\"" + API_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.fields[0].configured").value(true))
                .andExpect(jsonPath("$.data.fields[0].updatedBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.fields[1].configured").value(true))
                .andExpect(jsonPath("$.data.fields[2].configured").value(false))
                .andExpect(jsonPath("$.data.identityReady").value(false))
                .andExpect(jsonPath("$.data.paymentReady").value(false))
                .andReturn();
        MvcResult identityCard = mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{\"identityChannelKey\":\"" + CHANNEL_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityReady").value(true))
                .andExpect(jsonPath("$.data.paymentReady").value(false))
                .andReturn();
        MvcResult channelOnly = mvc.perform(withCsrf(put("/api/admin/portone-settings/payment"), session).content("{\"paymentChannelKey\":\"" + PAYMENT_CHANNEL_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields[3].configured").value(true))
                .andExpect(jsonPath("$.data.fields[4].configured").value(false))
                .andExpect(jsonPath("$.data.paymentReady").value(false)) // 웹훅 비밀키 없음
                .andReturn();
        MvcResult paymentCard = mvc.perform(withCsrf(put("/api/admin/portone-settings/payment"), session).content("{\"webhookSecret\":\"" + WEBHOOK_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentReady").value(true))
                .andReturn();
        MvcResult view = mvc.perform(get("/api/admin/portone-settings").cookie(session)).andExpect(status().isOk()).andReturn();

        for (MvcResult result : List.of(common, identityCard, channelOnly, paymentCard, view)) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(STORE_ID).doesNotContain(API_SECRET).doesNotContain(CHANNEL_KEY)
                    .doesNotContain(PAYMENT_CHANNEL_KEY).doesNotContain(WEBHOOK_SECRET).doesNotContain("whsec_").doesNotContain("v1:");
        }
        List<String> ciphers = jdbc.queryForList("SELECT value_cipher FROM portone_settings ORDER BY key", String.class);
        assertThat(ciphers).hasSize(5).allMatch(c -> c.startsWith("v1:"))
                .noneMatch(c -> c.contains(STORE_ID) || c.contains(API_SECRET) || c.contains(CHANNEL_KEY) || c.contains(PAYMENT_CHANNEL_KEY) || c.contains(WEBHOOK_SECRET));

        List<Map<String, Object>> audits = jdbc.queryForList("SELECT target_type, target_id, coalesce(detail, '') AS detail FROM audit_logs "
                + "WHERE actor_id = ? AND action = 'PORTONE_SETTINGS_UPDATE' ORDER BY id", operator);
        assertThat(audits).extracting(row -> row.get("target_id")).containsExactly("common", "identity", "payment", "payment");
        assertThat(audits).allMatch(row -> "PORTONE_SETTINGS".equals(row.get("target_type")));
        assertThat((String) audits.get(0).get("detail")).contains("storeId", "apiSecret").doesNotContain("identityChannelKey");
        assertThat((String) audits.get(1).get("detail")).contains("identityChannelKey").doesNotContain("storeId");
        assertThat((String) audits.get(2).get("detail")).contains("paymentChannelKey").doesNotContain("webhookSecret");
        assertThat((String) audits.get(3).get("detail")).contains("webhookSecret").doesNotContain("paymentChannelKey");
        String all = audits.stream().map(row -> (String) row.get("detail")).reduce("", String::concat);
        assertThat(all).doesNotContain(STORE_ID).doesNotContain(API_SECRET).doesNotContain(CHANNEL_KEY).doesNotContain(PAYMENT_CHANNEL_KEY).doesNotContain(WEBHOOK_SECRET);
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE actor_id = ? AND action = 'PORTONE_SETTINGS_UPDATE' ORDER BY id DESC LIMIT 1",
                String.class, operator)).as("결제를 받는 경로를 바꾸는 일 — 돈 관련").isEqualTo("MONEY");
    }

    @Test
    void 빈_값_긴_값_아무_칸도_없음_웹훅_비밀키_형식이_틀리면_400_이고_아무것도_저장하지_않는다() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_SECRET));

        mvc.perform(withCsrf(put("/api/admin/portone-settings/common"), session).content("{\"storeId\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.storeId").isNotEmpty());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/common"), session).content("{\"apiSecret\":\"" + "s".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.apiSecret").isNotEmpty());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/common"), session).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{\"storeId\":\"" + STORE_ID + "\"}"))
                .andExpect(status().isBadRequest()) // 본인인증 카드는 공통 칸을 받지 않는다(보낸 칸이 없음)
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        for (String bad : List.of("plain-secret-without-prefix", "whsec_", "whsec_not base64!")) {
            mvc.perform(withCsrf(put("/api/admin/portone-settings/payment"), session).content("{\"paymentChannelKey\":\"" + PAYMENT_CHANNEL_KEY
                            + "\",\"webhookSecret\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.webhookSecret").isNotEmpty())
                    .andExpect(jsonPath("$.error.fields.paymentChannelKey").doesNotExist());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portone_settings", Integer.class)).as("한 칸이라도 틀리면 그 카드는 저장하지 않는다").isZero();
    }

    @Test
    void 지우면_204_이고_감사에는_카드와_칸_이름만_다른_카드의_칸_설정_안_된_칸_모르는_카드는_404() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);
        mvc.perform(withCsrf(put("/api/admin/portone-settings/common"), session).content("{\"apiSecret\":\"" + API_SECRET + "\"}")).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/payment"), session).content("{\"webhookSecret\":\"" + WEBHOOK_SECRET + "\"}")).andExpect(status().isOk());

        // 공통 칸을 본인인증 카드로 지우지 않는다
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/identity/apiSecret"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/common/apiSecret"), session)).andExpect(status().isNoContent());
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/payment/webhookSecret"), session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/portone-settings").cookie(session))
                .andExpect(jsonPath("$.data.fields[1].configured").value(false))
                .andExpect(jsonPath("$.data.fields[1].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.fields[1].updatedBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.fields[4].configured").value(false));
        assertThat(jdbc.queryForObject("SELECT value_cipher FROM portone_settings WHERE key = 'apiSecret'", String.class)).isNull();
        List<Map<String, Object>> deletes = jdbc.queryForList("SELECT target_id, detail FROM audit_logs WHERE actor_id = ? AND action = 'PORTONE_SETTINGS_DELETE' ORDER BY id",
                operator);
        assertThat(deletes).extracting(row -> row.get("target_id")).containsExactly("common", "payment");
        assertThat((String) deletes.get(0).get("detail")).contains("\"field\":\"apiSecret\"").doesNotContain(API_SECRET);
        assertThat((String) deletes.get(1).get("detail")).contains("\"field\":\"webhookSecret\"").doesNotContain("whsec_");

        mvc.perform(withCsrf(delete("/api/admin/portone-settings/common/apiSecret"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/common/storeId"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/payment/paymentChannelKey"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/unknown/storeId"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
    }
}

package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.identity.config.IdentityProperties;
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
 * 포트원 설정 API(계약 「0-4」, 세션 정리 결정 9) — 값은 어떤 응답·감사에도 없고 설정 여부·시각·바꾼 사람만, 조회 {@code site.read} · 저장·지우기 {@code site.secret}.
 * 설정 표는 사이트에 하나라 시험 앞뒤로 원래 줄을 되돌린다.
 */
class PortOneSettingsHttpTest extends Commerce04AdminTestBase {

    private static final String STORE_ID = "store-c04-7f3a9e";
    private static final String API_SECRET = "secret-c04-QmFzZTY0U2VjcmV0VmFsdWU";
    private static final String CHANNEL_KEY = "channel-key-c04-b1d2e3";

    @Autowired
    private IdentityProperties identity;

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
    void 조회는_site_read_저장과_지우기는_site_secret_이_없으면_403() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));

        mvc.perform(get("/api/admin/portone-settings").cookie(none)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/admin/portone-settings").cookie(reader)).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), reader).content("{\"storeId\":\"" + STORE_ID + "\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/identity/storeId"), reader)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portone_settings", Integer.class)).isZero();
    }

    @Test
    void 처음에는_세_칸_모두_비었고_지금_구현과_암호화_키_여부를_알린다() throws Exception {
        mvc.perform(get("/api/admin/portone-settings").cookie(login(operatorWith(Permission.SITE_READ)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.identityProvider").value(identity.provider().name())) // 이 서버의 roboyam.identity.provider
                .andExpect(jsonPath("$.data.cryptoKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.identityReady").value(false))
                .andExpect(jsonPath("$.data.fields[0].key").value("storeId"))
                .andExpect(jsonPath("$.data.fields[1].key").value("apiSecret"))
                .andExpect(jsonPath("$.data.fields[2].key").value("identityChannelKey"))
                .andExpect(jsonPath("$.data.fields[0].configured").value(false))
                .andExpect(jsonPath("$.data.fields[0].updatedAt").isEmpty());
    }

    @Test
    void 보낸_칸만_암호화해_저장하고_값은_응답과_감사_어디에도_없다() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);

        MvcResult first = mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session)
                        .content("{\"storeId\":\"" + STORE_ID + "\",\"apiSecret\":\"" + API_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.fields[0].configured").value(true))
                .andExpect(jsonPath("$.data.fields[0].updatedBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.fields[1].configured").value(true))
                .andExpect(jsonPath("$.data.fields[2].configured").value(false))
                .andExpect(jsonPath("$.data.identityReady").value(false))
                .andReturn();
        MvcResult second = mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{\"identityChannelKey\":\"" + CHANNEL_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityReady").value(true))
                .andReturn();
        MvcResult view = mvc.perform(get("/api/admin/portone-settings").cookie(session)).andExpect(status().isOk()).andReturn();

        for (MvcResult result : List.of(first, second, view)) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(STORE_ID).doesNotContain(API_SECRET).doesNotContain(CHANNEL_KEY).doesNotContain("v1:");
        }
        List<String> ciphers = jdbc.queryForList("SELECT value_cipher FROM portone_settings ORDER BY key", String.class);
        assertThat(ciphers).hasSize(3).allMatch(c -> c.startsWith("v1:")).noneMatch(c -> c.contains(STORE_ID) || c.contains(API_SECRET) || c.contains(CHANNEL_KEY));

        List<String> audits = jdbc.queryForList("SELECT coalesce(detail, '') FROM audit_logs WHERE actor_id = ? AND action = 'PORTONE_SETTINGS_UPDATE' ORDER BY id",
                String.class, operator);
        assertThat(audits).hasSize(2);
        assertThat(audits.get(0)).contains("storeId", "apiSecret").doesNotContain("identityChannelKey");
        assertThat(audits.get(1)).contains("identityChannelKey");
        assertThat(String.join(" ", audits)).doesNotContain(STORE_ID).doesNotContain(API_SECRET).doesNotContain(CHANNEL_KEY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id = ? AND action = 'PORTONE_SETTINGS_UPDATE' AND target_type = 'PORTONE_SETTINGS'",
                Integer.class, operator)).isEqualTo(2);
    }

    @Test
    void 빈_값이나_아무_칸도_없으면_400() throws Exception {
        Cookie session = login(operatorWith(Permission.SITE_SECRET));

        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{\"storeId\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.storeId").isNotEmpty());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{\"apiSecret\":\"" + "s".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.apiSecret").isNotEmpty());
        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM portone_settings", Integer.class)).isZero();
    }

    @Test
    void 지우면_204_이고_감사에는_칸_이름만_설정돼_있지_않거나_모르는_칸은_404() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);
        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{\"apiSecret\":\"" + API_SECRET + "\"}")).andExpect(status().isOk());

        mvc.perform(withCsrf(delete("/api/admin/portone-settings/identity/apiSecret"), session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/portone-settings").cookie(session))
                .andExpect(jsonPath("$.data.fields[1].configured").value(false))
                .andExpect(jsonPath("$.data.fields[1].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.fields[1].updatedBy").value(operatorName(operator)));
        assertThat(jdbc.queryForObject("SELECT value_cipher FROM portone_settings WHERE key = 'apiSecret'", String.class)).isNull();
        String audit = jdbc.queryForObject("SELECT detail FROM audit_logs WHERE actor_id = ? AND action = 'PORTONE_SETTINGS_DELETE' ORDER BY id DESC LIMIT 1", String.class,
                operator);
        assertThat(audit).contains("\"field\":\"apiSecret\"").doesNotContain(API_SECRET);

        mvc.perform(withCsrf(delete("/api/admin/portone-settings/identity/apiSecret"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/identity/storeId"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
        mvc.perform(withCsrf(delete("/api/admin/portone-settings/identity/paymentChannelKey"), session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PORTONE_FIELD_NOT_FOUND"));
    }
}

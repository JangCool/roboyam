package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 설정 비밀값 키({@code ROBOYAM_CRYPTO_KEY})가 없는 관리자 서버 — 앱은 뜨고 포트원 화면은 그것을 알리며({@code cryptoKeyConfigured:false}), 저장만 503 {@code CRYPTO_KEY_MISSING}
 * (계약 「0-4」). 키가 빠진 문맥이라 따로 띄우고, 본인인증 구현은 portone 으로 둬 화면의 {@code identityProvider} 가 배포 설정을 따르는지도 본다.
 */
@TestPropertySource(properties = {"roboyam.crypto.key=", "roboyam.identity.provider=portone"})
class PortOneCryptoKeyMissingHttpTest extends Commerce04AdminTestBase {

    @Test
    void 암호화_키가_없으면_화면은_알리고_저장은_503_이고_아무것도_남지_않는다() throws Exception {
        long operator = operatorWith(Permission.SITE_READ, Permission.SITE_SECRET);
        Cookie session = login(operator);
        int before = jdbc.queryForObject("SELECT count(*) FROM portone_settings WHERE key = 'identityChannelKey'", Integer.class);

        mvc.perform(get("/api/admin/portone-settings").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.cryptoKeyConfigured").value(false))
                .andExpect(jsonPath("$.data.identityProvider").value("PORTONE")); // 화면의 지금 구현 = 이 서버의 roboyam.identity.provider
        mvc.perform(withCsrf(put("/api/admin/portone-settings/identity"), session).content("{\"identityChannelKey\":\"channel-no-key\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("CRYPTO_KEY_MISSING"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM portone_settings WHERE key = 'identityChannelKey'", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id = ? AND action = 'PORTONE_SETTINGS_UPDATE'", Integer.class, operator)).isZero();
    }
}

package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.mfa.controller.MfaTrustCookie;
import com.community.mfa.service.TrustedDeviceService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 신뢰 기기 쿠키 {@code ADMIN_MFA_TRUST} — 발급(HttpOnly·SameSite=Strict·path {@code /api/admin}·기한 30일, https 면 Secure), 이 브라우저의 기기를 끊거나 모두 끊으면
 * 응답이 쿠키를 지우고(Max-Age=0, 같은 path), 다른 기기를 끊을 때는 지우지 않는다(commerce-foundation-contract 「0-3」 신뢰 기기).
 */
class MfaTrustCookieHttpTest extends MfaMvcTestBase {

    @Autowired
    private TrustedDeviceService devices;

    private record Signed(Cookie session, Cookie trust, String setCookie) {
    }

    /** 켜짐·등록된 운영자가 https 로 로그인 → 2단계(다시 묻지 않기) → 로그인 세션 + 신뢰 기기 쿠키 */
    private Signed signInRemembering(long operator, String secret, boolean secure) throws Exception {
        Cookie pending = sessionCookie(login(operator, secure));
        MvcResult verified = mvc.perform(withCsrf(post("/api/admin/auth/mfa/verify"), pending).secure(secure)
                        .content("{\"code\":\"" + codeNow(secret) + "\",\"rememberDevice\":true}"))
                .andExpect(status().isOk()).andReturn();
        String header = verified.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(MfaTrustCookie.NAME + "=")).findFirst().orElseThrow();
        return new Signed(sessionCookie(verified), verified.getResponse().getCookie(MfaTrustCookie.NAME), header);
    }

    private static List<String> trustSetCookies(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(MfaTrustCookie.NAME + "=")).toList();
    }

    private String deviceId(long operator, String token) {
        return jdbc.queryForObject("SELECT id::text FROM operator_trusted_devices WHERE operator_id = ? AND token_hash = ?", String.class, operator,
                TrustedDeviceService.hash(token));
    }

    @Test
    void https_로_발급한_쿠키는_Secure_HttpOnly_SameSite_Strict_path_api_admin_30일() throws Exception {
        mfaOn();
        long operator = newOperator();
        Signed signed = signInRemembering(operator, enrollDirect(operator), true);

        assertThat(signed.trust().getSecure()).isTrue();
        assertThat(signed.trust().isHttpOnly()).isTrue();
        assertThat(signed.trust().getPath()).isEqualTo("/api/admin");
        assertThat(signed.trust().getMaxAge()).isEqualTo(30 * 24 * 3600);
        assertThat(signed.setCookie()).contains("Secure", "HttpOnly", "SameSite=Strict", "Path=/api/admin");
    }

    @Test
    void http_로_발급한_쿠키는_Secure_가_아니다() throws Exception {
        mfaOn();
        long operator = newOperator();
        Signed signed = signInRemembering(operator, enrollDirect(operator), false);

        assertThat(signed.trust().getSecure()).isFalse();
        assertThat(signed.setCookie()).doesNotContain("Secure");
    }

    @Test
    void 다른_기기를_끊으면_이_브라우저의_쿠키는_그대로다() throws Exception {
        mfaOn();
        long operator = newOperator();
        Signed signed = signInRemembering(operator, enrollDirect(operator), true);
        String otherId = deviceId(operator, devices.trust(operator, 30, IP, null));

        MvcResult result = mvc.perform(withCsrf(delete("/api/admin/mfa/trusted-devices/" + otherId), signed.session(), signed.trust()).secure(true))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(trustSetCookies(result)).isEmpty();
        assertThat(deviceCount(operator)).isEqualTo(1);
    }

    @Test
    void 이_브라우저의_기기를_끊으면_쿠키를_지운다() throws Exception {
        mfaOn();
        long operator = newOperator();
        Signed signed = signInRemembering(operator, enrollDirect(operator), true);
        String currentId = deviceId(operator, signed.trust().getValue());

        MvcResult result = mvc.perform(withCsrf(delete("/api/admin/mfa/trusted-devices/" + currentId), signed.session(), signed.trust()).secure(true))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(trustSetCookies(result)).singleElement().satisfies(h -> assertThat(h).contains("Max-Age=0", "Path=/api/admin"));
        assertThat(result.getResponse().getCookie(MfaTrustCookie.NAME).getMaxAge()).isZero();
        assertThat(result.getResponse().getCookie(MfaTrustCookie.NAME).getValue()).isEmpty();
        assertThat(deviceCount(operator)).isZero();
    }

    @Test
    void 모두_끊으면_쿠키를_지운다() throws Exception {
        mfaOn();
        long operator = newOperator();
        Signed signed = signInRemembering(operator, enrollDirect(operator), true);
        devices.trust(operator, 30, IP, null);

        MvcResult result = mvc.perform(withCsrf(delete("/api/admin/mfa/trusted-devices"), signed.session(), signed.trust()).secure(true))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(trustSetCookies(result)).singleElement().satisfies(h -> assertThat(h).contains("Max-Age=0", "Path=/api/admin"));
        assertThat(deviceCount(operator)).isZero();
    }
}

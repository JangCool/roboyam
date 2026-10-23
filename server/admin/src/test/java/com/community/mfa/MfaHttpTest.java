package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.installation.service.InstallationState;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * HTTP 로 본 2차 인증 — 보안 필터(로그인 전 허용 주소·CSRF)·세션 저장소(Spring Session JDBC)·인터셉터·오류 응답 모양({@code remainingAttempts}·{@code retryAfterSeconds})을
 * 실제 필터 체인으로 확인한다(commerce-foundation-contract 「0-3」 API). 설치 가드는 설치 완료로 둔다.
 */
class MfaHttpTest extends MfaSpringTestBase {

    private static final String XSRF = "test-xsrf-token";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter sessionFilter;

    private MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(sessionFilter, securityFilterChain).build();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie... cookies) {
        Cookie[] all = new Cookie[cookies.length + 1];
        all[0] = new Cookie("XSRF-TOKEN", XSRF);
        System.arraycopy(cookies, 0, all, 1, cookies.length);
        return request.cookie(all).header("X-XSRF-TOKEN", XSRF).contentType(MediaType.APPLICATION_JSON);
    }

    /** 세션 쿠키 — 운영 서버는 ADMIN_SESSION(server.servlet.session.cookie.name), 목 서블릿 환경의 세션 저장소는 기본 이름 SESSION 을 쓴다 */
    private static Cookie sessionCookie(MvcResult result) {
        Cookie admin = result.getResponse().getCookie("ADMIN_SESSION");
        return admin != null ? admin : result.getResponse().getCookie("SESSION");
    }

    private Cookie loginToPending(long operatorId) throws Exception {
        MvcResult result = mvc.perform(withCsrf(post("/api/admin/auth/login")).content("{\"email\":\"" + email(operatorId) + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.mfa.stage").value("VERIFY"))
                .andExpect(jsonPath("$.data.mfa.trustDays").value(30))
                .andReturn();
        Cookie session = sessionCookie(result);
        assertThat(session).as("2차 대기 세션 쿠키 %s", result.getResponse().getHeaders("Set-Cookie")).isNotNull();
        return session;
    }

    @Test
    void 대기_주소는_로그인_없이_열려_있지만_CSRF_는_필요하다() throws Exception {
        mvc.perform(post("/api/admin/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(withCsrf(post("/api/admin/auth/mfa/verify")).content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("MFA_PENDING_EXPIRED"));
        mvc.perform(withCsrf(post("/api/admin/auth/mfa/enrollment")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("MFA_PENDING_EXPIRED"));
        mvc.perform(withCsrf(post("/api/admin/auth/mfa/cancel"))).andExpect(status().isNoContent());
    }

    @Test
    void 로그인_세션_주소는_대기_세션으로_부르면_401_UNAUTHENTICATED() throws Exception {
        mfaOn();
        long operator = newOperator();
        enrollDirect(operator);
        Cookie pending = loginToPending(operator);

        mvc.perform(withCsrf(post("/api/admin/mfa/enrollment"), pending)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/admin/mfa/me").cookie(pending)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/me").cookie(pending)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.mfa.stage").value("VERIFY"));
    }

    @Test
    void 틀린_코드는_400_과_remainingAttempts_한도면_429_와_retryAfterSeconds() throws Exception {
        settings(true, 14, 1, 3, clock.instant().minus(Duration.ofDays(1)));
        long operator = newOperator();
        String secret = enrollDirect(operator);
        Cookie pending = loginToPending(operator);
        String wrong = "{\"code\":\"" + wrongCode(secret) + "\",\"rememberDevice\":false}";

        mvc.perform(withCsrf(post("/api/admin/auth/mfa/verify"), pending).content(wrong)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MFA_CODE_INVALID"))
                .andExpect(jsonPath("$.error.remainingAttempts").value(2));
        mvc.perform(withCsrf(post("/api/admin/auth/mfa/verify"), pending).content(wrong)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.remainingAttempts").value(1));
        mvc.perform(withCsrf(post("/api/admin/auth/mfa/verify"), pending).content(wrong)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("MFA_LOCKED"))
                .andExpect(jsonPath("$.error.retryAfterSeconds").value(600))
                .andExpect(header().string("Retry-After", "600"));
    }

    @Test
    void 맞는_코드로_통과하면_로그인_세션과_신뢰_기기_쿠키() throws Exception {
        mfaOn();
        long operator = newOperator();
        String secret = enrollDirect(operator);
        Cookie pending = loginToPending(operator);

        MvcResult result = mvc.perform(withCsrf(post("/api/admin/auth/mfa/verify"), pending)
                        .content("{\"code\":\"" + codeNow(secret) + "\",\"rememberDevice\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.id").value(Long.toString(operator)))
                .andExpect(jsonPath("$.data.recoveryCodesLeft").doesNotExist())
                .andReturn();
        Cookie session = sessionCookie(result);
        Cookie trust = result.getResponse().getCookie("ADMIN_MFA_TRUST");

        assertThat(session).isNotNull();
        assertThat(session.getValue()).isNotEqualTo(pending.getValue());
        assertThat(trust).isNotNull();
        mvc.perform(get("/api/admin/mfa/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true))
                .andExpect(jsonPath("$.data.siteEnabled").value(true));
    }
}

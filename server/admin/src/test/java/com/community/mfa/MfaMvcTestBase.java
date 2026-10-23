package com.community.mfa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.installation.service.InstallationState;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 실제 보안 필터 체인·세션 저장소를 거치는 HTTP 시험의 바탕(설치 가드는 설치 완료로 둔다) */
abstract class MfaMvcTestBase extends MfaSpringTestBase {

    protected static final String XSRF = "test-xsrf-token";

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

    protected MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(sessionFilter, securityFilterChain).build();
    }

    /** CSRF 쿠키·헤더와 JSON 본문 형식을 붙인다(null 쿠키는 건너뛴다) */
    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie... cookies) {
        List<Cookie> all = new ArrayList<>();
        all.add(new Cookie("XSRF-TOKEN", XSRF));
        Arrays.stream(cookies).filter(java.util.Objects::nonNull).forEach(all::add);
        return request.cookie(all.toArray(Cookie[]::new)).header("X-XSRF-TOKEN", XSRF).contentType(MediaType.APPLICATION_JSON);
    }

    /** 세션 쿠키 — 운영 서버는 ADMIN_SESSION, 목 서블릿 환경의 세션 저장소는 기본 이름 SESSION */
    protected static Cookie sessionCookie(MvcResult result) {
        Cookie admin = result.getResponse().getCookie("ADMIN_SESSION");
        return admin != null ? admin : result.getResponse().getCookie("SESSION");
    }

    /** 이메일 로그인 — 응답(세션 쿠키 포함) */
    protected MvcResult login(long operatorId, boolean secure, Cookie... cookies) throws Exception {
        return mvc.perform(withCsrf(post("/api/admin/auth/login"), cookies).secure(secure)
                        .content("{\"email\":\"" + email(operatorId) + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
    }
}

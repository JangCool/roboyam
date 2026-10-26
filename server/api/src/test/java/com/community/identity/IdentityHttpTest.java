package com.community.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.service.IdentityVerificationService;
import com.community.installation.service.InstallationState;
import com.community.user.entity.Role;
import com.community.withdrawal.service.IdentityWithdrawalRecorder;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.Filter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * 본인인증 API 의 HTTP 모양(계약 「0-4」 API 표) — 공통 래퍼·{@code Cache-Control: no-store}·오류의 추가 값이 오류 본문 최상위 칸으로 나가는지, 로그인 없으면 401.
 * 업무 규칙 자체는 {@link IdentityVerificationFlowTest}.
 */
class IdentityHttpTest extends CommerceApiTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    private IdentityVerificationService service;
    @Autowired
    private IdentityWithdrawalRecorder recorder;

    /** 보안 필터 없이 — 로그인 주체를 요청에 바로 싣는다 */
    private MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        installation.markCompleted();
        MockMvc secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        secured.perform(get("/api/identity-verifications/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 시작_확정_me_는_공통_래퍼이고_no_store_다() throws Exception {
        long member = newMember();
        mvc.perform(as(member, get("/api/identity-verifications/me"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.adult").doesNotExist());

        String started = mvc.perform(as(member, post("/api/identity-verifications"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.provider").value("FAKE"))
                .andExpect(jsonPath("$.data.storeId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = MAPPER.readTree(started).path("data").path("verificationId").asString();

        mvc.perform(as(member, post("/api/identity-verifications/" + id + "/confirm"))
                        .content("{\"fake\":{\"birthDate\":\"1990-05-14\",\"personKey\":\"" + newPerson() + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.adult").value(true))
                .andExpect(jsonPath("$.data.adultFrom").value("2009-05-14"))
                .andExpect(jsonPath("$.data.birthDate").doesNotExist());
    }

    @Test
    void 가짜_입력이_틀리면_400_VALIDATION_과_칸_경로() throws Exception {
        long member = newMember();
        String id = service.start(member).verificationId();
        mvc.perform(as(member, post("/api/identity-verifications/" + id + "/confirm")).content("{\"fake\":{\"birthDate\":\"oops\",\"personKey\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields['fake.birthDate']").exists())
                .andExpect(jsonPath("$.error.fields['fake.personKey']").exists());
    }

    @Test
    void 재인증_제한은_409_와_오류_본문_최상위_retryAfter() throws Exception {
        String person = newPerson();
        long left = newMember();
        service.confirm(left, service.start(left).verificationId(), new FakeIdentityInput("1990-05-14", person));
        recorder.recordIdentity(left, clock.instant());

        long member = newMember();
        String id = service.start(member).verificationId();
        String expected = ServiceTime.firstFullDateAfter(clock.instant().plus(Duration.ofDays(30))).toString();
        mvc.perform(as(member, post("/api/identity-verifications/" + id + "/confirm"))
                        .content("{\"fake\":{\"birthDate\":\"1990-05-14\",\"personKey\":\"" + person + "\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_REVERIFY_BLOCKED"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.retryAfter").value(expected))
                .andExpect(jsonPath("$.error.fields").doesNotExist());
    }

    @Test
    void 남의_번호는_404_이미_인증은_409() throws Exception {
        long owner = newMember();
        String id = service.start(owner).verificationId();
        mvc.perform(as(newMember(), post("/api/identity-verifications/" + id + "/confirm")).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("IDENTITY_VERIFICATION_NOT_FOUND"));

        service.confirm(owner, id, new FakeIdentityInput("1990-05-14", newPerson()));
        mvc.perform(as(owner, post("/api/identity-verifications"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_ALREADY_VERIFIED"));
    }

    @Test
    void 발급_30분이_지나면_410() throws Exception {
        long member = newMember();
        String id = service.start(member).verificationId();
        clock.advance(Duration.ofMinutes(31));
        mvc.perform(as(member, post("/api/identity-verifications/" + id + "/confirm"))
                        .content("{\"fake\":{\"birthDate\":\"1990-05-14\",\"personKey\":\"" + newPerson() + "\"}}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("IDENTITY_VERIFICATION_EXPIRED"));
    }
}

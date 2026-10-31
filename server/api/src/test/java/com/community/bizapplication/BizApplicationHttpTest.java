package com.community.bizapplication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.installation.service.InstallationState;
import com.community.user.entity.Role;
import jakarta.servlet.Filter;
import java.time.LocalDate;
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

/**
 * 사업자 센터 신청 건 HTTP(user-web {@code useBusinessApi} 모양) — 로그인 필요 401, 신청 201 {@code {id}}, 목록 묶음·건수, 상세 날짜(끝 포함)·금액·처리 기록, 결제 시작 모양,
 * 남의 건 404, 자격 409, 칸 오류 400, 상품·남은 칸·링크 검사.
 */
class BizApplicationHttpTest extends BizApplicationApiTestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private MockMvc mvc;
    private MockMvc secured;

    @BeforeEach
    void setUpMvc() {
        installation.markCompleted();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        secured.perform(get("/api/business/applications")).andExpect(status().isUnauthorized());
        secured.perform(get("/api/business/ad-products")).andExpect(status().isUnauthorized());
    }

    @Test
    void 신청_목록_상세_결제_시작_모양() throws Exception {
        long user = approvedBusiness();
        LocalDate start = today().plusDays(5);
        String body = "{\"adType\":\"TEXT_LINE\",\"startDate\":\"" + start + "\",\"endDate\":\"" + start.plusDays(2) + "\",\"imageFileId\":null,\"linkUrl\":\"https://a.example.com\","
                + "\"altText\":null,\"text\":\"한 줄 광고\",\"paymentMethod\":\"ONLINE\",\"agreedAdPolicyVersion\":\"v1\",\"agreedRefundPolicyVersion\":\"v1\"}";
        String created = mvc.perform(as(user, post("/api/business/applications/ads")).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString()).andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
        mvc.perform(as(user, get("/api/business/applications")).param("bucket", "REVIEW")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.counts.ALL").value(1))
                .andExpect(jsonPath("$.data.counts.REVIEW").value(1)).andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].adType").value("TEXT_LINE")).andExpect(jsonPath("$.data.items[0].startDate").value(start.toString()))
                .andExpect(jsonPath("$.data.items[0].endDate").value(start.plusDays(2).toString())).andExpect(jsonPath("$.data.items[0].amountConfirmed").value(false))
                .andExpect(jsonPath("$.data.items[0].amount.total").value(16_500));
        mvc.perform(as(user, get("/api/business/applications/" + id))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.creative.text").value("한 줄 광고")).andExpect(jsonPath("$.data.history[0].action").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.history[0].actor").value("ME")).andExpect(jsonPath("$.data.estimate.supply").value(15_000));
        approve(Long.parseLong(id), "ONLINE", freshPeriod(3));
        mvc.perform(as(user, post("/api/business/applications/" + id + "/payment"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderType").value("BIZ_APPLICATION")).andExpect(jsonPath("$.data.orderRefId").value(id))
                .andExpect(jsonPath("$.data.checkout.gateway").value("FAKE")).andExpect(jsonPath("$.data.checkout.amount").value(16_500));
        long other = approvedBusiness();
        mvc.perform(as(other, get("/api/business/applications/" + id))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_NOT_FOUND"));
    }

    @Test
    void 자격_409_칸_오류_400() throws Exception {
        long none = newMember();
        mvc.perform(as(none, post("/api/business/applications/deals")).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_NOT_APPROVED"));
        long user = approvedBusiness();
        mvc.perform(as(user, post("/api/business/applications/ads")).content("{\"adType\":\"TEXT_LINE\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.period").exists())
                .andExpect(jsonPath("$.error.fields.agree").exists());
    }

    @Test
    void 상품_남은_칸_링크_검사() throws Exception {
        long user = approvedBusiness();
        mvc.perform(as(user, get("/api/business/ad-products"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].type").value("WIDE")).andExpect(jsonPath("$.data[0].dailyPrice").value(50_000))
                .andExpect(jsonPath("$.data[0].capacity").value(2));
        mvc.perform(as(user, get("/api/business/deal-products"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.postPrice").value(30_000))
                .andExpect(jsonPath("$.data.placements[0].placement").value("HOME_TOP"));
        LocalDate from = today().plusDays(1);
        mvc.perform(as(user, get("/api/business/availability")).param("slot", "HOME_TOP").param("from", from.toString()).param("to", from.plusDays(6).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(7)).andExpect(jsonPath("$.data[0].capacity").value(3));
        mvc.perform(as(user, get("/api/business/availability")).param("slot", "NOPE").param("from", from.toString()).param("to", from.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.slot").exists());
        mvc.perform(as(user, get("/api/business/deal-link-check")).param("url", "https://bit.ly/x")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true)).andExpect(jsonPath("$.data.blockedReason").isString());
    }
}

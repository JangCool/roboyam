package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 회원 상세 응답의 본인 인증 칸 — {@code identity: { verified, verifiedAt, adult, provider } | null}(계약 「0-4」). 탈퇴 회원은 null, 생년월일·DI·CI 해시는 싣지 않는다.
 */
class MemberDetailIdentityTest extends Commerce04AdminTestBase {

    private MvcResult detail(long memberId) throws Exception {
        Cookie reader = login(operatorWith(Permission.MEMBER_READ));
        return mvc.perform(get("/api/admin/members/" + memberId).cookie(reader)).andExpect(status().isOk()).andReturn();
    }

    @Test
    void 인증한_성인은_인증_여부_시각_성인_수단만_있다_생년월일과_해시는_없다() throws Exception {
        long member = newMember();
        LocalDate birth = ServiceTime.today(Clock.systemUTC()).minusYears(30);
        Instant at = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
        verified(member, birth, "PORTONE", at);

        MvcResult result = mvc.perform(get("/api/admin/members/" + member).cookie(login(operatorWith(Permission.MEMBER_READ)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identity.verified").value(true))
                .andExpect(jsonPath("$.data.identity.verifiedAt").value(at.toString()))
                .andExpect(jsonPath("$.data.identity.adult").value(true))
                .andExpect(jsonPath("$.data.identity.provider").value("PORTONE"))
                .andExpect(jsonPath("$.data.identity.birthDate").doesNotExist())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(birth.toString()).doesNotContain(String.format("%064x", member)).doesNotContain("tx-" + member);
    }

    @Test
    void 미성년은_adult_false_가짜_구현은_FAKE() throws Exception {
        long member = newMember();
        verified(member, ServiceTime.today(Clock.systemUTC()).minusYears(19).plusDays(1), "FAKE", Instant.now());

        mvc.perform(get("/api/admin/members/" + member).cookie(login(operatorWith(Permission.MEMBER_READ)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identity.verified").value(true))
                .andExpect(jsonPath("$.data.identity.adult").value(false))
                .andExpect(jsonPath("$.data.identity.provider").value("FAKE"));
    }

    @Test
    void 미인증은_verified_false_나머지_null() throws Exception {
        long member = newMember();
        String body = detail(member).getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<Boolean>read(body, "$.data.identity.verified")).isFalse();
        assertThat(com.jayway.jsonpath.JsonPath.<Object>read(body, "$.data.identity.verifiedAt")).isNull();
        assertThat(com.jayway.jsonpath.JsonPath.<Object>read(body, "$.data.identity.adult")).isNull();
        assertThat(com.jayway.jsonpath.JsonPath.<Object>read(body, "$.data.identity.provider")).isNull();
    }

    @Test
    void 탈퇴_회원은_identity_가_null_이다_인증_기록이_남아_있어도() throws Exception {
        long member = withdrawnMember(Instant.now().minus(Duration.ofDays(1)));
        verified(member, LocalDate.of(1990, 5, 5), "PORTONE", Instant.now().minus(Duration.ofDays(100)));

        String body = detail(member).getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<Object>read(body, "$.data.identity")).isNull();
        assertThat(body).contains("\"identity\":null").doesNotContain("1990-05-05");
    }
}

package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.service.IdentityVerificationService;
import com.community.terms.service.TermsService;
import com.community.user.entity.Role;
import com.community.zconfiguration.crypto.DataCipher;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 로보티콘 S2 의 공개 사이트 API(emoticon-contract — 상품화 계약 · 정산 정보 · 약관 종류 ROBOTIKON_CONTRACT) — 앱 문맥 전체 + 실제 PostgreSQL + MockMvc.
 * 본인인증 확정이 인증 이름을 암호문으로 남기고(가짜 구현), 정산 정보 저장이 그 이름과 예금주를 대조하며, 응답에는 가린 값만 나간다. 상품화 계약은 가입 동의·공개 요약에 들지 않는다.
 */
class EmoticonStudioPaidHttpTest extends CommerceApiTestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private IdentityVerificationService identity;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private TermsService terms;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private long member() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname, grade_key) VALUES (?, ?, 'REGULAR')", id, "p" + base36.substring(Math.max(0, base36.length() - 11)));
        return id;
    }

    private static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B as(long memberId, B request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    /** 가짜 본인인증 — 이름 {@code name}(null 이면 가짜 구현의 기본 이름 홍길동) */
    private void verify(long userId, String name) {
        identity.confirm(userId, identity.start(userId).verificationId(), new FakeIdentityInput("1990-05-14", "emo-" + userId, name));
    }

    private int publishContract(String body) {
        Integer last = jdbc.queryForObject("SELECT coalesce(max(version), 0) FROM terms_versions WHERE kind = 'ROBOTIKON_CONTRACT'", Integer.class);
        jdbc.update("INSERT INTO terms_versions (id, kind, version, body, effective_date, published_at) VALUES (?, 'ROBOTIKON_CONTRACT', ?, ?, ?, now())", ids.nextId(),
                last + 1, body, LocalDate.now(ZoneId.of("Asia/Seoul")));
        return last + 1;
    }

    @Test
    void 본인인증_확정은_인증_이름을_암호문으로만_남긴다() {
        long a = member();
        verify(a, null);
        String stored = jdbc.queryForObject("SELECT name_enc FROM identity_verifications WHERE user_id = ?", String.class, a);
        assertThat(stored).startsWith("d").doesNotContain("홍길동");
        assertThat(cipher.decrypt(IdentityVerificationService.NAME, stored)).isEqualTo("홍길동");
        long b = member();
        verify(b, " 김작가 ");
        assertThat(cipher.decrypt(IdentityVerificationService.NAME, jdbc.queryForObject("SELECT name_enc FROM identity_verifications WHERE user_id = ?", String.class, b)))
                .isEqualTo("김작가");
    }

    @Test
    void 정산_정보는_로그인_본인인증_예금주_대조_응답은_가린_값만_no_store() throws Exception {
        String body = "{\"type\":\"INDIVIDUAL\",\"businessNumber\":null,\"bankCode\":\"KB\",\"accountNumber\":\"123-4567-890123\",\"holderName\":\"홍길동\"}";
        mvc.perform(get("/api/me/emoticon-studio/payout-account")).andExpect(status().isUnauthorized());
        long creator = member();
        mvc.perform(as(creator, get("/api/me/emoticon-studio/payout-account"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.registered").value(false))
                .andExpect(jsonPath("$.data.identityNameMasked").doesNotExist());
        mvc.perform(as(creator, put("/api/me/emoticon-studio/payout-account")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_REQUIRED"));
        verify(creator, null);
        mvc.perform(as(creator, put("/api/me/emoticon-studio/payout-account")).contentType(MediaType.APPLICATION_JSON).content(body.replace("홍길동", "김철수")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.holderName").isNotEmpty());
        MvcResult saved = mvc.perform(as(creator, put("/api/me/emoticon-studio/payout-account")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.type").value("INDIVIDUAL")).andExpect(jsonPath("$.data.bankCode").value("KB"))
                .andExpect(jsonPath("$.data.accountMasked").value("****0123")).andExpect(jsonPath("$.data.holderMasked").value("홍*동"))
                .andExpect(jsonPath("$.data.identityNameMasked").value("홍*동")).andExpect(jsonPath("$.data.businessNumberMasked").doesNotExist()).andReturn();
        assertThat(saved.getResponse().getContentAsString()).doesNotContain("4567890123", "홍길동");
        mvc.perform(as(creator, get("/api/me/emoticon-studio/payout-account"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.accountMasked").value("****0123"));
    }

    @Test
    void 만_19세_미만_작가는_정산_정보를_등록할_수_없다_403_IDENTITY_UNDERAGE_와_adultFrom() throws Exception {
        String body = "{\"type\":\"INDIVIDUAL\",\"businessNumber\":null,\"bankCode\":\"KB\",\"accountNumber\":\"123-4567-890123\",\"holderName\":\"홍길동\"}";
        long minor = member();
        identity.confirm(minor, identity.start(minor).verificationId(), new FakeIdentityInput("2012-03-01", "emo-" + minor, null));
        mvc.perform(as(minor, put("/api/me/emoticon-studio/payout-account")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_UNDERAGE")).andExpect(jsonPath("$.error.adultFrom").value("2031-03-01"))
                .andExpect(jsonPath("$.error.message").value("만 19세 미만은 유료 판매와 정산 정보 등록을 할 수 없어요. 무료 팩은 만들 수 있어요."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", Integer.class, minor)).isZero();
    }

    @Test
    void 계약은_지금_판_제목이_없으면_기본_문구_요약_평문_내_동의() throws Exception {
        int v = publishContract("<ul><li>사이트가 판매자예요.</li></ul><p>제1조 &lt;목적&gt;</p>");
        long creator = member();
        mvc.perform(get("/api/me/emoticon-studio/contract")).andExpect(status().isUnauthorized());
        mvc.perform(as(creator, get("/api/me/emoticon-studio/contract"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(v))
                .andExpect(jsonPath("$.data.title").value("로보티콘 상품화 계약")).andExpect(jsonPath("$.data.summary[0]").value("사이트가 판매자예요."))
                .andExpect(jsonPath("$.data.body").value("• 사이트가 판매자예요.\n\n제1조 <목적>")).andExpect(jsonPath("$.data.agreements").isEmpty());
    }

    @Test
    void 상품화_계약은_공개_약관_화면이_있고_가입_동의_버전과_공개_요약에는_없다() throws Exception {
        publishContract("<h2>로보티콘 상품화 계약</h2><p>본문</p>");
        mvc.perform(get("/api/terms/robotikon_contract")).andExpect(status().isOk()).andExpect(jsonPath("$.data.kind").value("ROBOTIKON_CONTRACT"));
        MvcResult summary = mvc.perform(get("/api/terms/summary")).andExpect(status().isOk()).andReturn();
        assertThat(summary.getResponse().getContentAsString()).doesNotContain("ROBOTIKON_CONTRACT");
        assertThat(terms.currentAgreementVersion()).matches("T\\d+\\.P\\d+");
    }
}

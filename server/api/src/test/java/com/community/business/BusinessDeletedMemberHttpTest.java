package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.business.dto.BusinessApplyRequest;
import com.community.business.dto.MyBusinessView;
import com.community.business.repository.master.BusinessStore;
import com.community.business.service.MyBusinessService;
import com.community.commerce.CommerceApiTestBase;
import com.community.user.entity.Role;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 사업자 삭제 뒤 회원 쪽(business-contract 「사업자 삭제」) — 관리자 삭제가 쓰는 {@link BusinessStore#deleteProfile} 로 승인된 신원을 지우면 그 회원의
 * {@code GET /api/business/me} 는 미신청({@code NONE})이 되고 다시 신청할 수 있다. 실제 공개 사이트 앱 문맥 + PostgreSQL(국세청은 세는 가짜 구현).
 */
@Import(BusinessNtsValidateTest.CountingNtsConfig.class)
class BusinessDeletedMemberHttpTest extends CommerceApiTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MyBusinessService service;
    @Autowired
    private BusinessStore store;
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** 체크섬이 맞는 새 등록번호(끝에서 둘째 자리 0 — 가짜 구현: 계속 + 일치) */
    private static String bizNo() {
        int[] w = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append((char) ('0' + ThreadLocalRandom.current().nextInt(i == 0 ? 1 : 0, 10)));
        }
        sb.append('0');
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (sb.charAt(i) - '0') * w[i];
        }
        sum += ((sb.charAt(8) - '0') * 5) / 10;
        return sb.append((char) ('0' + (10 - sum % 10) % 10)).toString();
    }

    private BusinessApplyRequest request(long user) {
        String cert = service.upload(user, new MockMultipartFile("file", "cert.png", "image/png", PNG)).fileId();
        return new BusinessApplyRequest("(주)삭제뒤상사", bizNo(), "홍길동", "01012345678", "biz" + user + "@test.local",
                new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", ""), cert, "v1", "v1", "2021-03-02");
    }

    @Test
    void 지운_뒤_내_사업자는_NONE_이고_다시_신청할_수_있다() throws Exception {
        long user = newMember();
        String base = Long.toString(user, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", user, "d" + base.substring(Math.max(0, base.length() - 11)));
        service.apply(user, request(user));
        jdbc.update("UPDATE business_profiles SET status = 'APPROVED', reviewed_at = now(), first_approved_at = now() WHERE user_id = ?", user);
        long profileId = jdbc.queryForObject("SELECT id FROM business_profiles WHERE user_id = ?", Long.class, user);
        AuthUser principal = new AuthUser(user, Role.USER);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        mvc.perform(get("/api/business/me").principal(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(store.hasDealings(profileId)).isFalse();
        int deleted = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .execute(status -> store.deleteProfile(profileId));
        assertThat(deleted).isEqualTo(1);

        mvc.perform(get("/api/business/me").principal(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NONE"));

        MyBusinessView.Profile again = service.apply(user, request(user));
        assertThat(again.status()).isEqualTo("PENDING");
    }
}

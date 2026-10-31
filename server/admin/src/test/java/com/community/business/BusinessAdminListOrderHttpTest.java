package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 사업자 목록({@code GET /api/admin/businesses})의 정렬 — {@code status=PENDING}(인증 심사 큐)은 신청이 오래된 것 먼저, 그 밖의 상태는 최근 것 먼저
 * (docs/plans/biz-ad-hotdeal/04-ia-review.md 5절). 실제 관리자 앱 문맥 + PostgreSQL.
 */
class BusinessAdminListOrderHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private long profile(String company, String status, Instant submittedAt) {
        long user = newMember();
        long profile = ids.nextId();
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, false, ?, 'x', ?, '1234', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), ?, now(), now())", profile, user, status, company,
                String.format("%064x", profile), ts(submittedAt));
        return profile;
    }

    private List<String> ids(Cookie session, String status, String q) throws Exception {
        String body = mvc.perform(get("/api/admin/businesses").param("status", status).param("q", q).param("size", "100").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readTree(body).get("data").get("items").valueStream().map(n -> n.get("id").asString()).toList();
    }

    @Test
    void 심사_대기는_오래된_것_먼저_다른_상태는_최근_것_먼저() throws Exception {
        String tag = "정렬" + Long.toString(ids.nextId(), 36);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long pendingNew = profile(tag + "대기새", "PENDING", now.minus(1, ChronoUnit.HOURS));
        long pendingOld = profile(tag + "대기옛", "PENDING", now.minus(3, ChronoUnit.DAYS));
        long pendingMid = profile(tag + "대기중", "PENDING", now.minus(1, ChronoUnit.DAYS));
        long approvedOld = profile(tag + "승인옛", "APPROVED", now.minus(5, ChronoUnit.DAYS));
        long approvedNew = profile(tag + "승인새", "APPROVED", now.minus(2, ChronoUnit.HOURS));
        Cookie reader = login(operatorWith(Permission.BUSINESS_READ));

        assertThat(ids(reader, "PENDING", tag)).containsExactly(Long.toString(pendingOld), Long.toString(pendingMid), Long.toString(pendingNew));
        assertThat(ids(reader, "APPROVED", tag)).containsExactly(Long.toString(approvedNew), Long.toString(approvedOld));
    }
}

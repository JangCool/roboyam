package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.service.BizApplicationMemberService;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 신청 건 API(실제 관리자 앱 문맥 + PostgreSQL) — 권한(조회 business.read · 처리 ad.manage · 환불 deal-order.refund), 목록 큐·건수와 상세 모양, 승인 → 수동 결제 →
 * 계약서 multipart → 열람(data URL) → 입금 → 확정, 회원 목록·상세의 별도 칸 {@code businessVerified} 와 거르기(역할 기준 {@code memberType} 은 그대로).
 */
class BizApplicationAdminHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private BizApplicationMemberService members;

    private long approvedBusiness() {
        long user = newMember();
        long profile = ids.nextId();
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                + "VALUES (?, ?, 'APPROVED', false, '관리상사', 'x', ?, '1234', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())", profile, user,
                String.format("%064x", profile));
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, ?, 'APPROVED', now())", ids.nextId(), profile, service);
        }
        return user;
    }

    private long submit(long user) {
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3);
        return members.submitAd(user, new BizAdApplyRequest("TEXT_LINE", start.toString(), start.plusDays(2).toString(), null, "https://a.example.com", null, "관리 시험 한 줄",
                "MANUAL", "v1", "v1"));
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder csrfMultipart(
            org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request, Cookie session) {
        request.cookie(new Cookie("XSRF-TOKEN", XSRF), session).header("X-XSRF-TOKEN", XSRF);
        return request;
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return MAPPER.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    @Test
    void 권한_조회는_business_read_처리는_ad_manage_환불은_deal_order_refund() throws Exception {
        long id = submit(approvedBusiness());
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.BUSINESS_READ));
        mvc.perform(get("/api/admin/biz-applications").cookie(none)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/admin/biz-applications").param("queue", "REVIEW").cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.counts.REVIEW").exists())
                .andExpect(jsonPath("$.data.counts.DEPOSIT_OVERDUE").exists());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/reject"), reader).content("{\"code\":\"OTHER\",\"text\":\"권한 없음\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/refund/deny"), login(operatorWith(Permission.BUSINESS_READ, Permission.AD_MANAGE)))
                .content("{\"reason\":\"권한 없음\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/biz-applications/12345").cookie(reader)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_NOT_FOUND"));
        mvc.perform(get("/api/admin/biz-applications").param("queue", "NOPE").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.queue").exists());
    }

    @Test
    void 승인_수동_결제_계약서_올리기_열람_입금_확정까지_HTTP() throws Exception {
        long user = approvedBusiness();
        long id = submit(user);
        Cookie op = login(operatorWith(Permission.BUSINESS_READ, Permission.AD_MANAGE));
        JsonNode detail = data(mvc.perform(get("/api/admin/biz-applications/" + id).cookie(op)).andExpect(status().isOk()));
        assertThat(detail.get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(detail.get("kind").asString()).isEqualTo("AD_TEXT_LINE");
        assertThat(detail.get("adType").asString()).isEqualTo("TEXT_LINE");
        assertThat(detail.get("business").get("maskedRegNo").asString()).isEqualTo("***-**-*1234");
        assertThat(detail.get("content").get("type").asString()).isEqualTo("AD_TEXT_LINE");
        assertThat(detail.get("quote").get("unit").asString()).isEqualTo("DAY");
        assertThat(detail.get("history").get(0).get("action").asString()).isEqualTo("SUBMITTED");
        Instant start = Instant.parse(detail.get("period").get("startAt").asString());
        Instant end = Instant.parse(detail.get("period").get("endAt").asString());
        long supply = detail.get("quote").get("supply").asLong();
        String deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        JsonNode approved = data(mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/approve"), op).content("{\"method\":\"MANUAL\",\"supply\":" + supply
                + ",\"deadlineAt\":\"" + deadline + "\",\"period\":{\"startAt\":\"" + start + "\",\"endAt\":\"" + end + "\"}}")).andExpect(status().isOk()));
        assertThat(approved.get("status").asString()).isEqualTo("AWAITING_DEPOSIT");
        assertThat(approved.get("approval").get("method").asString()).isEqualTo("MANUAL");

        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/confirm"), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BIZ_DEPOSIT_INCOMPLETE"));
        MockMultipartFile bad = new MockMultipartFile("files", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        mvc.perform(csrfMultipart(multipart("/api/admin/biz-applications/" + id + "/contracts").file(bad), op))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        MockMultipartFile pdf = new MockMultipartFile("files", "계약서.pdf", "application/pdf", "%PDF-1.4 admin".getBytes(StandardCharsets.US_ASCII));
        JsonNode withContract = data(mvc.perform(csrfMultipart(multipart("/api/admin/biz-applications/" + id + "/contracts").file(pdf), op))
                .andExpect(status().isOk()));
        String fileId = withContract.get("contracts").get(0).get("id").asString();
        assertThat(withContract.get("contracts").get(0).get("replacedAt").isNull()).isTrue();
        mvc.perform(get("/api/admin/biz-applications/" + id + "/contracts/" + fileId).cookie(op)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("data:application/pdf;base64,")));
        long total = approved.get("approval").get("amount").get("total").asLong();
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        mvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/biz-applications/" + id + "/deposit"), op)
                .content("{\"amount\":" + (total - 1) + ",\"depositedOn\":\"" + today + "\",\"depositorName\":\"관리상사\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.mismatchReason").exists());
        mvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/biz-applications/" + id + "/deposit"), op)
                .content("{\"amount\":" + total + ",\"depositedOn\":\"" + today + "\",\"depositorName\":\"관리상사\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deposit.amount").value(total));
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/confirm"), op)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_type = 'BIZ_APPLICATION' AND target_id = ?", Integer.class, Long.toString(id)))
                .isGreaterThanOrEqualTo(5);
        JsonNode live = data(mvc.perform(get("/api/admin/biz-applications").param("queue", "LIVE").cookie(op)).andExpect(status().isOk()));
        assertThat(live.get("items").valueStream().map(n -> n.get("id").asString()).toList()).contains(Long.toString(id));
    }

    @Test
    void 회원_목록의_사업자_승인_칸과_거르기는_사업자_신원_APPROVED_기준이고_역할_기준_회원_유형은_그대로() throws Exception {
        long business = approvedBusiness();
        long plain = newMember();
        long reReview = newMember();
        long profile = ids.nextId();
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                + "VALUES (?, ?, 'PENDING', true, '재심사', 'x', ?, '1234', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())", profile, reReview,
                String.format("%064x", profile));
        Cookie reader = login(operatorWith(Permission.MEMBER_READ));
        JsonNode yes = data(mvc.perform(get("/api/admin/members").param("businessVerified", "true").param("size", "100").param("sort", "joinedAt,desc").cookie(reader))
                .andExpect(status().isOk()));
        java.util.List<String> verified = yes.get("items").valueStream().map(n -> n.get("memberNo").asString()).toList();
        assertThat(verified).contains(Long.toString(business)).doesNotContain(Long.toString(plain), Long.toString(reReview));
        assertThat(yes.get("items").valueStream().allMatch(n -> n.get("businessVerified").asBoolean())).isTrue();
        JsonNode business0 = yes.get("items").valueStream().filter(n -> n.get("memberNo").asString().equals(Long.toString(business))).findFirst().orElseThrow();
        assertThat(business0.get("memberType").asString()).isEqualTo("GENERAL"); // 역할 기준 회원 유형은 바꾸지 않았다
        JsonNode no = data(mvc.perform(get("/api/admin/members").param("businessVerified", "false").param("size", "100").param("sort", "joinedAt,desc").cookie(reader))
                .andExpect(status().isOk()));
        java.util.List<String> others = no.get("items").valueStream().map(n -> n.get("memberNo").asString()).toList();
        assertThat(others).contains(Long.toString(plain), Long.toString(reReview)).doesNotContain(Long.toString(business));
        mvc.perform(get("/api/admin/members").param("businessVerified", "maybe").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.businessVerified").exists());
        mvc.perform(get("/api/admin/members/" + business).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.businessVerified").value(true));
        mvc.perform(get("/api/admin/members/" + plain).cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.businessVerified").value(false));
    }
}

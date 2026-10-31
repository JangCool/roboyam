package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
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
 * 관리자 신청 건 API(실제 관리자 앱 문맥 + PostgreSQL) — 권한(도메인별 조회 ad.read|hotdeal.read · 처리 ad.manage|hotdeal.manage · 환불 deal-order.refund — 04-ia-review S2),
 * 목록의 {@code domain}(필수)·도메인 밖 종류 400, <b>다른 도메인 403</b>(광고 운영자가 핫딜 건의 목록·상세·기간 판정·처리, 반대도), 도메인별 운영자 알림·감사 detail,
 * 목록 큐·건수와 상세 모양, 승인 → 수동 결제 → 계약서 multipart → 열람(data URL) → 입금 → 확정, 회원 목록·상세의 별도 칸 {@code businessVerified} 와 거르기(역할 기준
 * {@code memberType} 은 그대로).
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

    /** 사업자 핫딜 글(노출 없음) */
    private long submitDeal(long user) {
        return members.submitDeal(user, new BizDealApplyRequest("[관리상사] 텀블러 1+1", new BizDealInfo("https://smartstore.naver.com/roboyam/products/9", "네이버 스마트스토어",
                32_000L, 15_900L, 0L, null, "LIMITED", "1+1 행사", "NONE"), null, null, "MANUAL", "v1", "v1"));
    }

    private java.util.List<String> ids(JsonNode page) {
        return page.get("items").valueStream().map(n -> n.get("id").asString()).toList();
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
    void 권한_조회는_ad_read_처리는_ad_manage_환불은_deal_order_refund_목록은_domain_필수() throws Exception {
        long id = submit(approvedBusiness());
        Cookie none = login(operatorWith());
        Cookie business = login(operatorWith(Permission.BUSINESS_READ));
        Cookie reader = login(operatorWith(Permission.AD_READ));
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").cookie(none)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        // 사업자 인증 권한만으로는 신청 건을 못 본다(예전 입구 business.read)
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").cookie(business)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/biz-applications/" + id).cookie(business)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("queue", "REVIEW").cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.counts.REVIEW").exists())
                .andExpect(jsonPath("$.data.counts.DEPOSIT_OVERDUE").exists());
        // domain 없음·틀림 400 fields.domain, 그 도메인이 아닌 종류 400 fields.kind
        mvc.perform(get("/api/admin/biz-applications").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.domain").exists());
        mvc.perform(get("/api/admin/biz-applications").param("domain", "NOPE").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.domain").exists());
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("kind", "HOTDEAL_POST").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.kind").exists());
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("kind", "AD_TEXT_LINE").cookie(reader)).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/reject"), reader).content("{\"code\":\"OTHER\",\"text\":\"권한 없음\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/refund/deny"), login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE)))
                .content("{\"reason\":\"권한 없음\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/biz-applications/12345").cookie(reader)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_NOT_FOUND"));
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("queue", "NOPE").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.queue").exists());
    }

    @Test
    void 다른_도메인_건은_목록_상세_기간_판정_처리_모두_403_반대도() throws Exception {
        long user = approvedBusiness();
        long ad = submit(user);
        long deal = submitDeal(user);
        long adOperator = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        long dealOperator = operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE);
        Cookie adOp = login(adOperator);
        Cookie dealOp = login(dealOperator);
        String reject = "{\"code\":\"OTHER\",\"text\":\"다른 도메인\"}";

        // 광고 운영자 → 핫딜 건
        mvc.perform(get("/api/admin/biz-applications").param("domain", "HOTDEAL").cookie(adOp)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/admin/biz-applications/" + deal).cookie(adOp)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(get("/api/admin/biz-applications/" + deal + "/availability").cookie(adOp)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + deal + "/reject"), adOp).content(reject)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + deal + "/cancel"), adOp).content("{\"reason\":\"다른 도메인\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + deal + "/confirm"), adOp)).andExpect(status().isForbidden());
        // 핫딜 운영자 → 광고 건
        mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").cookie(dealOp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/biz-applications/" + ad).cookie(dealOp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/biz-applications/" + ad + "/availability").cookie(dealOp)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + ad + "/reject"), dealOp).content(reject)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/biz-applications/" + ad + "/deposit"), dealOp)
                .content("{\"amount\":1000,\"depositedOn\":\"2026-01-01\",\"depositorName\":\"관리상사\"}")).andExpect(status().isForbidden());
        // 거절된 처리는 아무것도 바꾸지 않았다 + 거부 감사(대상 = 그 신청 건)
        assertThat(jdbc.queryForObject("SELECT status FROM biz_applications WHERE id = ?", String.class, deal)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT status FROM biz_applications WHERE id = ?", String.class, ad)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'PERMISSION_DENIED' AND actor_id = ? AND target_type = 'BIZ_APPLICATION' AND target_id = ?",
                Integer.class, adOperator, Long.toString(deal))).isGreaterThanOrEqualTo(1);

        // 자기 도메인은 된다 — 목록에는 자기 도메인 건만, 큐별 건수도 그 안에서
        JsonNode adPage = data(mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("queue", "REVIEW").cookie(adOp)).andExpect(status().isOk()));
        assertThat(ids(adPage)).contains(Long.toString(ad)).doesNotContain(Long.toString(deal));
        assertThat(adPage.get("items").valueStream().allMatch(n -> n.get("kind").asString().startsWith("AD_"))).isTrue();
        JsonNode dealPage = data(mvc.perform(get("/api/admin/biz-applications").param("domain", "HOTDEAL").param("queue", "REVIEW").cookie(dealOp))
                .andExpect(status().isOk()));
        assertThat(ids(dealPage)).contains(Long.toString(deal)).doesNotContain(Long.toString(ad));
        assertThat(dealPage.get("items").valueStream().allMatch(n -> n.get("kind").asString().startsWith("HOTDEAL_"))).isTrue();
        long adReview = jdbc.queryForObject("SELECT count(*) FROM biz_applications WHERE status = 'SUBMITTED' AND kind IN ('AD_BANNER', 'AD_TEXT_LINE')", Long.class);
        assertThat(adPage.get("counts").get("REVIEW").asLong()).isEqualTo(adReview);
        mvc.perform(get("/api/admin/biz-applications/" + ad).cookie(adOp)).andExpect(status().isOk()).andExpect(jsonPath("$.data.kind").value("AD_TEXT_LINE"));
        mvc.perform(get("/api/admin/biz-applications/" + deal).cookie(dealOp)).andExpect(status().isOk()).andExpect(jsonPath("$.data.kind").value("HOTDEAL_POST"));
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + deal + "/reject"), dealOp).content(reject)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // 조회만 있고 처리 권한이 없는 도메인 — 상세는 되고 처리는 403
        Cookie dealReader = login(operatorWith(Permission.AD_MANAGE, Permission.HOTDEAL_READ));
        mvc.perform(get("/api/admin/biz-applications/" + deal).cookie(dealReader)).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + deal + "/cancel"), dealReader).content("{\"reason\":\"처리 권한 없음\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 운영자_알림은_건의_도메인으로_광고는_ad_manage_핫딜은_hotdeal_manage_에게_그_심사_화면_링크로() {
        long adHandler = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        long dealHandler = operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE);
        long user = approvedBusiness();
        long ad = submit(user);
        long deal = submitDeal(user);
        String sql = "SELECT event_code || ' ' || coalesce(link_path, '') FROM admin_notifications WHERE operator_id = ? AND dedupe_key = ?";
        assertThat(jdbc.queryForList(sql, String.class, adHandler, Long.toString(ad))).containsExactly("AD_REVIEW_PENDING /ad-reviews?id=" + ad);
        assertThat(jdbc.queryForList(sql, String.class, dealHandler, Long.toString(ad))).isEmpty();
        assertThat(jdbc.queryForList(sql, String.class, dealHandler, Long.toString(deal))).containsExactly("HOTDEAL_REVIEW_PENDING /hotdeal-reviews?id=" + deal);
        assertThat(jdbc.queryForList(sql, String.class, adHandler, Long.toString(deal))).isEmpty();
    }

    @Test
    void 승인_수동_결제_계약서_올리기_열람_입금_확정까지_HTTP() throws Exception {
        long user = approvedBusiness();
        long id = submit(user);
        Cookie op = login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE));
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
        // 감사 detail 에 건의 도메인·출처(S6)
        JsonNode audit = MAPPER.readTree(jdbc.queryForObject("SELECT detail FROM audit_logs WHERE action = 'BIZ_APP_APPROVE' AND target_id = ? ORDER BY id DESC LIMIT 1",
                String.class, Long.toString(id)));
        assertThat(audit.get("domain").asString()).isEqualTo("AD");
        assertThat(audit.get("source").asString()).isEqualTo("BUSINESS");
        assertThat(audit.get("method").asString()).isEqualTo("MANUAL");
        JsonNode live = data(mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("queue", "LIVE").cookie(op)).andExpect(status().isOk()));
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
